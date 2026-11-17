package com.luoch.cbt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("session_record")
public class SessionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String threadId;
    private String sessionSummary;
    private String confirmedFacts;
    private String userReportedThoughts;
    private String userReportedEmotions;
    private String evidenceReview;
    private String acceptedAlternativeThought;
    private String intervention;
    private String actionResult;
    private String unresolvedIssues;
    private String nextStep;
    private String confidenceOfRecord;
    private Integer recordApproved;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String v) { this.threadId = v; }
    public String getSessionSummary() { return sessionSummary; }
    public void setSessionSummary(String v) { this.sessionSummary = v; }
    public String getConfirmedFacts() { return confirmedFacts; }
    public void setConfirmedFacts(String v) { this.confirmedFacts = v; }
    public String getUserReportedThoughts() { return userReportedThoughts; }
    public void setUserReportedThoughts(String v) { this.userReportedThoughts = v; }
    public String getUserReportedEmotions() { return userReportedEmotions; }
    public void setUserReportedEmotions(String v) { this.userReportedEmotions = v; }
    public String getEvidenceReview() { return evidenceReview; }
    public void setEvidenceReview(String v) { this.evidenceReview = v; }
    public String getAcceptedAlternativeThought() { return acceptedAlternativeThought; }
    public void setAcceptedAlternativeThought(String v) { this.acceptedAlternativeThought = v; }
    public String getIntervention() { return intervention; }
    public void setIntervention(String v) { this.intervention = v; }
    public String getActionResult() { return actionResult; }
    public void setActionResult(String v) { this.actionResult = v; }
    public String getUnresolvedIssues() { return unresolvedIssues; }
    public void setUnresolvedIssues(String v) { this.unresolvedIssues = v; }
    public String getNextStep() { return nextStep; }
    public void setNextStep(String v) { this.nextStep = v; }
    public String getConfidenceOfRecord() { return confidenceOfRecord; }
    public void setConfidenceOfRecord(String v) { this.confidenceOfRecord = v; }
    public Integer getRecordApproved() { return recordApproved; }
    public void setRecordApproved(Integer v) { this.recordApproved = v; }
}
