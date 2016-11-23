package org.ow2.proactive.scheduler.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.ow2.proactive.scheduler.common.Main;
import org.ow2.proactive.scheduler.common.NotificationData;
import org.ow2.proactive.scheduler.common.SchedulerEvent;
import org.ow2.proactive.scheduler.common.job.JobInfo;
import org.ow2.proactive.scheduler.common.job.JobState;
import org.ow2.proactive.scheduler.core.properties.PASchedulerProperties;
import org.ow2.proactive.scheduler.util.JobLogger;
import org.ow2.proactive.scheduler.util.SendMail;
import org.apache.log4j.Logger;


public class JobEmailNotification {

    public static final String GENERIC_INFORMATION_KEY_EMAIL = "EMAIL";
    public static final String GENERIC_INFORMATION_KEY_NOTIFICATION_EVENT = "NOTIFICATION_EVENT";

    private JobState jobState;
    private SchedulerEvent eventType;
    private SendMail sender;

    private static final Logger logger = Logger.getLogger(JobEmailNotification.class);
    private static final JobLogger jlogger = JobLogger.getInstance();

    private static final String SUBJECT_TEMPLATE = "ProActive Job %s : %s";
    private static final String BODY_TEMPLATE = "New Status: %s\n\n" + "--\n"
        + "This email was auto-generated by ProActive Scheduling\n" + "Version: %s\n" + "Hostname: %s";

    public JobEmailNotification(JobState js, NotificationData<JobInfo> notification, SendMail sender) {
        this.jobState = js;
        this.eventType = notification.getEventType();
        this.sender = sender;
    }

    public JobEmailNotification(JobState js, NotificationData<JobInfo> notification) {
        this(js, notification, new SendMail());
    }

    public boolean doCheckAndSend() throws JobEmailNotificationException {
        switch (eventType) {
            case JOB_PAUSED:
            case JOB_RESUMED:
            case JOB_IN_ERROR:
            case JOB_SUBMITTED:
            case JOB_PENDING_TO_RUNNING:
            case JOB_CHANGE_PRIORITY:
            case JOB_PENDING_TO_FINISHED:
            case JOB_RUNNING_TO_FINISHED:
            case JOB_RESTARTED_FROM_ERROR:
                break;
            default:
                logger.trace("Event unrelated to job finish, doing nothing");
                return false;
        }
        if (!PASchedulerProperties.EMAIL_NOTIFICATIONS_ENABLED.getValueAsBoolean()) {
            logger.debug("Notification emails disabled, doing nothing");
            return false;
        }
        try {
            sender.sender(getTo(), getSubject(), getBody());
            return true;
        } catch (AddressException e) {
            throw new JobEmailNotificationException("Malformed email address", e);
        } catch (MessagingException e) {
            throw new JobEmailNotificationException("Error sending email: " + e.getMessage(), e);
        }
    }

    public void checkAndSend() {
        try {
            boolean sent = doCheckAndSend();
            if (sent) {
                jlogger.info(jobState.getId(), "sent notification email for finished job");
            }
        } catch (JobEmailNotificationException e) {
            jlogger.warn(jobState.getId(), "failed to send email notification: " + e.getMessage());
            logger.trace("Stack trace:", e);
        }
    }

    private static String getFrom() throws JobEmailNotificationException {
        String from = PASchedulerProperties.EMAIL_NOTIFICATIONS_SENDER_ADDRESS.getValueAsString();
        if (from == null || from.isEmpty()) {
            throw new JobEmailNotificationException("Sender address not set in scheduler configuration");
        }
        return from;
    }

    private String getTo() throws JobEmailNotificationException {
        String to = jobState.getGenericInformation().get(GENERIC_INFORMATION_KEY_EMAIL);
        if (to == null) {
            throw new JobEmailNotificationException("Recipient address is not set in generic information");
        }
        return to;
    }

    private String getSubject() {
        String jobID = jobState.getId().value();
        String event = eventType.toString();
        return String.format(SUBJECT_TEMPLATE, jobID, event);
    }

    private String getBody() {
        String status = jobState.getStatus().toString();
        String version = Main.version;
        String hostname = "UNKNOWN";
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            logger.debug("Could not get hostname", e);
        }
        return String.format(BODY_TEMPLATE, status, version, hostname);
    }
}