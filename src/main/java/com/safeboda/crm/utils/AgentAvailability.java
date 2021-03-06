package com.safeboda.crm.utils;

/**
 * @author Gibson Wasukira
 * @created 23/06/2021 - 6:59 PM
 */

public class AgentAvailability {

    private String scheduleFromDate;
    private String scheduleToDate;
    private String agentID;
    private String agentName;
    private String availabitityDate;
    private String dateEntered;
    private String availabile;
    private int count;


    public AgentAvailability(String scheduleFromDate, String scheduleToDate, String agentName,String agentID, String availabitityDate, String dateEntered, String availabile) {
        this.scheduleFromDate = scheduleFromDate;
        this.scheduleToDate = scheduleToDate;
        this.agentName = agentName;
        this.agentID = agentID;
        this.availabitityDate = availabitityDate;
        this.dateEntered = dateEntered;
        this.availabile = availabile;
    }

    public String getScheduleFromDate() {
        return scheduleFromDate;
    }

    public void setScheduleFromDate(String scheduleFromDate) {
        this.scheduleFromDate = scheduleFromDate;
    }

    public String getGetScheduleToDate() {
        return scheduleToDate;
    }

    public void setGetScheduleToDate(String scheduleToDate) {
        this.scheduleToDate = scheduleToDate;
    }

    public String getAgentID() {
        return agentID;
    }

    public void setAgentID(String agentID) {
        this.agentID = agentID;
    }

    public String getAvailabitityDate() {
        return availabitityDate;
    }

    public void setAvailabitityDate(String availabitityDate) {
        this.availabitityDate = availabitityDate;
    }

    public String getDateEntered() {
        return dateEntered;
    }

    public void setDateEntered(String dateEntered) {
        this.dateEntered = dateEntered;
    }

    public String getAvailabile() {
        return availabile;
    }

    public void setAvailabile(String availabile) {
        this.availabile = availabile;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    @Override
    public String toString() {
        return "AgentAvailability{" +
                "scheduleFromDate='" + scheduleFromDate + '\'' +
                ", scheduleToDate='" + scheduleToDate + '\'' +
                ", agentID='" + agentID + '\'' +
                ", agentName='" + agentName + '\'' +
                ", availabitityDate='" + availabitityDate + '\'' +
                ", dateEntered='" + dateEntered + '\'' +
                ", availabile='" + availabile + '\'' +
                '}';
    }
}
