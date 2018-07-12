//----------------------------------------------------
// Script Console
//----------------------------------------------------

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.*
import com.atlassian.jira.issue.fields.AggregateTimeSpentSystemField
import com.atlassian.jira.issue.util.AggregateTimeTrackingCalculatorFactory
import com.atlassian.jira.ComponentManager

def issueManager = ComponentAccessor.getIssueManager()
MutableIssue issue = issueManager.getIssueObject("STOR-6307")

def field = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Stories Estimate");

log.warn(field.getIdAsLong());
log.warn(field.customFieldType.key);

def value = issue.getCustomFieldValue(field);

log.warn(value);
//------------------
MutableIssue issue2 = issueManager.getIssueObject("STOR-4258")
def field2 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Epic Link");
log.warn(field2.customFieldType.key);
def value2 = issue2.getCustomFieldValue(field2);
log.warn(value2);

//------------------??????
Issue issue3 = issueManager.getIssueObject("STOR-6013")
log.warn(issue3.class);
def timeTrackingCalculatorFactory = ComponentManager.getComponentInstanceOfType(AggregateTimeTrackingCalculatorFactory.class)
def calculator = timeTrackingCalculatorFactory.getCalculator(issue3)
log.warn("direct: " + issue3.timeSpent);
log.warn("with sub-tasks: " + calculator.getAggregates(issue3).timeSpent);

//----------------------------------------------------
// Calculate the cycle timeSpent
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.*
import com.atlassian.jira.issue.fields.AggregateTimeSpentSystemField
import com.atlassian.jira.issue.util.AggregateTimeTrackingCalculatorFactory

import com.atlassian.core.util.DateUtils
import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.history.ChangeItemBean

import java.text.*

def issueManager = ComponentAccessor.getIssueManager()
MutableIssue issue = issueManager.getIssueObject("STOR-9522")

def componentManager = ComponentManager.getInstance()
def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()

def doneStatuses = ["story: done", "tech debt: done", "devops: done"]
def inProgressStatuses = ["story: in development", "tech debt: in development", "devops: in progress"]

if(!(issue.status.name.toLowerCase() in doneStatuses))
	return null

Date startDate = null
Date endDate = null

changeHistoryManager.getChangeItemsForField(issue, "status").each {ChangeItemBean item ->
    log.warn("'${item.fromString}' --> '${item.toString}' ${item.created}");
    
    if(item.toString.toLowerCase() in inProgressStatuses && (startDate == null || item.created < startDate))
    	startDate = new Date(item.created.getTime()).clearTime()
    if(item.toString.toLowerCase() in doneStatuses && (endDate == null || item.created > endDate))
    	endDate = new Date(item.created.getTime()).clearTime()
}
//----------------------------------------------------------------------------------------------------
// https://stackoverflow.com/questions/1617049/calculate-the-number-of-business-days-between-two-dates

def df = new SimpleDateFormat("dd.MM.yyy");
def holidays = [
    df.parse("25.12.2017"), // Christmas Day
    df.parse("26.12.2017"), // Boxing Day
    df.parse("01.01.2018"), // New Year
    df.parse("19.02.2018"), // Famaily Day
    df.parse("30.03.2018"), // Good Friday
    df.parse("21.05.2018"), // Victoria Day
    df.parse("02.07.2018"), // Canada Day
    df.parse("06.08.2018"), // Civic Holiday
    df.parse("03.09.2018"), // Labour Day
    df.parse("08.10.2018"), // Thanksgiving
    df.parse("25.12.2018"), // Christmas Day
    df.parse("26.12.2018") // Boxing Day
]

//df = new SimpleDateFormat("dd.MM.yyy HH:mm:ss");
//startDate = df.parse("15.02.2018 00:00:00").clearTime()
//endDate = df.parse("16.02.2018 00:00:00").clearTime()
//def formattedDate = startDate.format("dd/MM/yyy HH:mm:ss")

log.warn("Start Date: ${startDate} - ${startDate.getClass()}")
log.warn("End Date: ${endDate} - ${startDate.getClass()}")
def span = endDate - startDate

log.warn("Span: ${span}")

def businessDays = span + 1
int fullWeekCount = businessDays / 7 as int
// find out if there are weekends during the time exceedng the full weeks
if (businessDays > fullWeekCount * 7)
{
    // we are here to find out if there is a 1-day or 2-days weekend
    // in the time interval remaining after subtracting the complete weeks
    int startDayOfWeek = startDate[Calendar.DAY_OF_WEEK]
    int endDayOfWeek = endDate[Calendar.DAY_OF_WEEK]
    // We need the day of week starts in Monday
    startDayOfWeek = startDayOfWeek == 1 ? 7 : startDayOfWeek - 1
    endDayOfWeek = endDayOfWeek == 1 ? 7 : endDayOfWeek - 1

    if (endDayOfWeek < startDayOfWeek)
        endDayOfWeek += 7;
    if (startDayOfWeek <= 6)
    {
        if (endDayOfWeek >= 7) // Both Saturday and Sunday are in the remaining time interval
        	businessDays -= 2;
        else if (endDayOfWeek >= 6) // Only Saturday is in the remaining time interval
            businessDays -= 1;
    }
    else if (startDayOfWeek <= 7 && endDayOfWeek >= 7) // Only Sunday is in the remaining time interval
        businessDays -= 1;
}

// subtract the weekends during the full weeks in the interval
businessDays -= 2 * fullWeekCount;

// subtract holidays during the time interval
holidays.each{Date holiday ->
    if (startDate <= holiday && holiday <= endDate)
    	--businessDays;
}

log.warn(businessDays)
return businessDays
//----------------------------------------------------
// Get Change History
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.*
import com.atlassian.jira.issue.fields.AggregateTimeSpentSystemField
import com.atlassian.jira.issue.util.AggregateTimeTrackingCalculatorFactory

import com.atlassian.core.util.DateUtils
import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.changehistory.ChangeHistory;
import com.atlassian.jira.issue.history.ChangeItemBean

import java.text.*

def issueManager = ComponentAccessor.getIssueManager()
MutableIssue issue = issueManager.getIssueObject("STOR-9522")

def componentManager = ComponentManager.getInstance()
def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()

Date startDate = null
Date endDate = null

changeHistoryManager.getChangeHistories(issue).each {ChangeHistory ch ->
    log.warn("'${ch.getAuthorDisplayName()}' ------------> '${ch.getTimePerformed()}'");
    ch.getChangeItemBeans().each {ChangeItemBean item ->
        log.warn("FIELD='${item.getField()}':::: ${item.fromString}' --> '${item.toString}' ${item.created}");
    }
}

//----------------------------------------------------
// Sprint Start and Close dates: https://community.atlassian.com/t5/Jira-questions/Sprint-Date-Custom-field/qaq-p/134718
// Required Imports
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.greenhopper.service.sprint.Sprint
import java.text.SimpleDateFormat;
import java.util.Date.*;

def issueManager = ComponentAccessor.getIssueManager()

// Get the current issue key
MutableIssue issue = issueManager.getIssueObject("STOR-1300")
//MutableIssue issue = issueManager.getIssueObject("STOR-11125")

//Issue issue = null;
//----------------
def jqlSearch = 'project = Storyteller AND issuetype in (Story, Spike, "Tech Debt", Bug) AND Sprint in (Quasar-Sprint-4)';
SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();

// Set the default list of issues to null just to be safe 
//List<Issue> issues = null;

// Perform the search as a user and return the result so it can be validated. 
SearchService.ParseResult parseResult = searchService.parseQuery(user, jqlSearch);

if (parseResult.isValid()) {

    def searchResult = searchService.search(user, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
    //issue = searchResult.issues.first();
} 
else { 
    // Log errors if invalid JQL is used so we can fix it
    log.error("Invalid JQL: " + jqlSearch);
    //issue = null;
}
//----------------


// Get a pointer to the CustomFieldManager
def customFieldManager = ComponentAccessor.getCustomFieldManager()

// Get a pointer to the sprint CF
def sprintCf = customFieldManager.getCustomFieldObjectByName("Sprint")

// Stop the values on screen showing old cached values
//enableCache = {-> false}

// Define a new Java Simple Date Format
SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MMM/YYYY HH:mm ZZZ");
sdfDate.setTimeZone (TimeZone.getTimeZone ("America/Toronto"));

// Get the Start and End dates for the sprint
log.warn(issue.getCustomFieldValue(sprintCf).size());
issue.getCustomFieldValue(sprintCf)?.each{def s ->
	log.warn(s.name);
}
//log.warn(issue.getCustomFieldValue(sprintCf).startDate);

def sprint = issue.getCustomFieldValue(sprintCf)
Date startDate = sprint?.startDate?.first()?.toDate();
Date endDate = sprint?.endDate?.first()?.toDate()

// Format the dates with the Java Simple Date format defined
def formattedstartDate = sdfDate.format(startDate)
def formattedendDate = sdfDate.format(endDate)

// Construct the output String to be returned
String dates = "The Sprint Start Date is: " + formattedstartDate + "<br>" + "The Sprint End Date is: " + formattedendDate

// Return the output String

startDate.upto(endDate) {
    // Print day of the week.
    //log.warn(it.format('dd/MMM/YYYY HH:mm ZZZ'));
    //sdfDate.format
    log.warn(sdfDate.format(it));
}

return dates
//----------------------------------------------------