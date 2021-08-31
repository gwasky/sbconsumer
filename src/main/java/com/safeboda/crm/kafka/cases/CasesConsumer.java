package com.safeboda.crm.kafka.cases;

/**
 * @author Gibson Wasukira
 * @created 23/06/2021 - 6:59 PM
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeboda.crm.entities.CaseAudit;
import com.safeboda.crm.entities.QueueAudit;
import com.safeboda.crm.utils.AgentAvailability;
import com.safeboda.crm.utils.DBUtils;
import com.safeboda.crm.utils.Utils;
import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

import static com.safeboda.crm.utils.Constants.*;

public class CasesConsumer {

    private static Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
    private static int count = 0;

    public static void main(String[] args) {

        Logger logger = LoggerFactory.getLogger(CasesConsumer.class.getName());

        Utils utils = new Utils();
        DBUtils dbUtils = new DBUtils();
        Properties props = utils.loadProperties();

        String consumerTopic = props.getProperty("kafka.backoffice.topic");
        Properties consumerProps = utils.getConsumerProperties();
        // Create Consumer
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(consumerProps);
        consumer.subscribe(Arrays.asList(consumerTopic));

//        try {
//            logger.info("################### DEPLOYMENT ENV #################");
//            logger.info("################### {} #################", System.getenv("OP_ENV"));
//            logger.info("################### DEPLOYMENT ENV #################");
//            while (true) {
//                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));
//                String availabilityDate = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
//                String availabilityDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(Calendar.getInstance().getTime());
//                for (ConsumerRecord<String, String> record : records) {
//
//                    logger.info("Key: " + record.key() + ",  Value: " + record.value());
//                    logger.info("Partition: " + record.partition() + ", Offest " + record.offset());
//                }
//            }
//
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        } finally {
//            // Closing will Clean up Sockets in use, alert the coordinator about the consumer's departure from a group
//            consumer.close();
//        }

        try {

            logger.info("################### DEPLOYMENT ENV #################");
            logger.info("################### {} #################", System.getenv("OP_ENV"));
            logger.info("################### DEPLOYMENT ENV #################");

            //  poll for new data
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                String availabilityDate = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());
                String availabilityDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(Calendar.getInstance().getTime());
                for (ConsumerRecord<String, String> record : records) {
                    // logger.info("Key: " + record.key() + ",  Value: " + record.value());
                    logger.info("Partition: " + record.partition() + ", Offest " + record.offset());

                    String agentAssignmentTracker = null;
                    // Deserialize object
                    ObjectMapper objectMapper = new ObjectMapper();
                    QueueAudit queueAudit = objectMapper.readValue(record.value(), QueueAudit.class);
                    try {
                        String caseStatus = dbUtils.getCaseStatus(queueAudit.getCaseId());
                        if (caseStatus.equals("Closed_Closed")) {
                            logger.info("CaseId[{}] - Case Already Closed - Will not be Re-assigned", queueAudit.getCaseId());
                            continue;
                        }
                        String deptName = utils.getDeptName(props, queueAudit.getBoQueueId());
                        if (deptName != null) {
                            ArrayList<AgentAvailability> scheduledAgentsAvailability = dbUtils.getScheduledAgentsAndAvailability(availabilityDate, deptName);
                            logger.info("{} - Number of Scheduled Agents for dept[{}] - {}", queueAudit.getCaseId(), deptName, scheduledAgentsAvailability.size());
                            if (scheduledAgentsAvailability.size() > 0) {
                                // logger.info(String.valueOf(scheduledAgentsAvailability));
                                boolean exists = utils.checkForObjectRedisPersistence(availabilityDate);
                                if (!exists) {
                                    try {
                                        agentAssignmentTracker = utils.initializeObjectInRedis(availabilityDate, scheduledAgentsAvailability);
                                    } catch (Exception ex) {
                                        logger.error("{} - Error Initializing Object in Redis - {}", queueAudit.getCaseId(), ex.getMessage());
                                        ex.printStackTrace();
                                        TimeUnit.MINUTES.sleep(1);
                                        utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                                    }
                                } else {
                                    try {
                                        agentAssignmentTracker = utils.getAvailabilityObjectFromRedis(availabilityDate);
                                    } catch (Exception ex) {
                                        logger.error("{} - Error Fetching Availability Object from Redis - {}", queueAudit.getCaseId(), ex.getMessage());
                                        ex.printStackTrace();
                                        TimeUnit.MINUTES.sleep(1);
                                        utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                                    }
                                }
                                // Update Redis Tracker with Newly Available Agents
                                String agentAvailabilityList = utils.updateAvailabilityTrackerWithNewlyAvailableAgents(availabilityDate, scheduledAgentsAvailability, agentAssignmentTracker);
                                logger.info("{} - agentAvailabilityList - {}", queueAudit.getCaseId(), agentAvailabilityList);
                                if (!agentAvailabilityList.equals("[]")) {
                                    // Check there's an Agent available from the Department/Queue the ticket is assigned to
                                    String userId = utils.nominateUserForAssignment(agentAvailabilityList, deptName);
                                    if (userId != null) {
                                        // Log the Ticket to Audit Audit Table

                                        CaseAudit caseAudit = new CaseAudit(queueAudit.getCaseId(), userId, caseStatus, SOURCE);
                                        int result = dbUtils.logToAuditTable(caseAudit);
                                        logger.info("Log to Audit Table {}|{}|{}|{}", queueAudit.getCaseId(), userId, caseStatus, result);
                                        // Assign to Agent
                                        String response = utils.logon();
                                        Map<String, String> responseMap = utils.jsonStringToMap(response);
                                        logger.info(responseMap.toString());
                                        String jsonObject = utils.buildCaseJSONObject
                                                (responseMap.get("id"), queueAudit.getCaseId(), userId).toString();
                                        int statusCode = utils.updateCase(jsonObject);
                                        // boolean assignmentStatus = dbUtils.assignCaseToAgent(queueAudit.getCaseId(), userId);
                                        logger.info("CaseID[{}] | UserID[{}] | StatusCode[{}]", queueAudit.getCaseId(), userId, statusCode);
                                        if (statusCode == 200) {
                                            // Update Assignment Counts for the day
                                            String updatedAgentTracker = utils.updateAssignmentCounts(availabilityDate, agentAvailabilityList, userId);
                                            logger.info("Successful | refreshed Counts | {}", updatedAgentTracker);
                                        } else {
                                            logger.info("CaseID[{}] Assignment to Agent[{}] Failed", queueAudit.getCaseId(), userId);
                                            // Send to Retry Topic
                                            TimeUnit.MINUTES.sleep(1);
                                            utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                                        }
                                    } else {
                                        logger.error("No UserID picked up at Nomination");
                                        logger.info("Recycling Case .........................");
                                        TimeUnit.MINUTES.sleep(1);
                                        utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                                    }
                                } else {
                                    logger.error("{} - No Object found for Agent Assignment Tracker", queueAudit.getCaseId());
                                    TimeUnit.MINUTES.sleep(1);
                                    utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                                }
                            } else {
                                logger.info("There are no Available Agents for dept[{}] - {} - {}", deptName, availabilityDateTime, queueAudit.getCaseId());
                                TimeUnit.MINUTES.sleep(1);
                                utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                            }
                        } else {
                            logger.info("User[{}] Department Not Configured for User", queueAudit.getBoQueueId());
                        }
                    } catch (SQLException ex) {
                        logger.error("getScheduledAgentsAndAvailability - Exception - {}", ex.getMessage());
                        TimeUnit.MINUTES.sleep(1);
                        utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        TimeUnit.MINUTES.sleep(1);
                        utils.produceRecord(props.getProperty("kafka.backoffice.topic"), new ObjectMapper().writeValueAsString(queueAudit));
                    }

                    // Keep Track of Current Offset

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Closing will Clean up Sockets in use, alert the coordinator about the consumer's departure from a group
            consumer.close();
        }
    }
}
