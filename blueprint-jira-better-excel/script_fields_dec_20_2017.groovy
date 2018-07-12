// Stories Estimate

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.component.ComponentAccessor;

def componentManager = ComponentManager.getInstance()
def issueLinkManager = ComponentAccessor.getIssueLinkManager();
def cfManager = ComponentAccessor.getCustomFieldManager()

if(issue.issueType.name != "Epic"){
    return null;
}

Double totalSP = 0

customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");
log.debug("${customField.id}")

issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
    log.debug("Link of type ${issueLink.issueLinkType.name} from ${issueLink.sourceObject} to ${issueLink.destinationObject}")
    log.debug("${issueLink.destinationObject.issueType.name}")
    if (issueLink.issueLinkType.name == "Epic-Story Link" && 
       issueLink.destinationObject.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps"]
       && !(issueLink.destinationObject.status.name in ["Story: Cancelled", "Tech Debt: Cancelled"])) {
		log.debug("${issueLink.destinationObject.getCustomFieldValue(customField).toString()}")
        def SP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
        totalSP += SP as Double;
    }
}

return totalSP == 0 ? null : totalSP;

// Epic Remaining Estimate

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.component.ComponentAccessor;

def componentManager = ComponentManager.getInstance()
def issueLinkManager = ComponentAccessor.getIssueLinkManager();
def cfManager = ComponentAccessor.getCustomFieldManager()

if(issue.issueType.name != "Epic"){
    return null;
}

if(issue.status.name == "Epic: Done"){
    return 0
}

Double totalSP = 0
Double totalClosedSP = 0

customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");
log.debug("${customField.id}")

def customField2 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Estimate in Days");
log.debug("${customField.id}")

issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
    log.debug("Link of type ${issueLink.issueLinkType.name} from ${issueLink.sourceObject} to ${issueLink.destinationObject}")
    log.debug("${issueLink.destinationObject.issueType.name}")
    if (issueLink.issueLinkType.name == "Epic-Story Link" && 
       issueLink.destinationObject.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps"]
       && !(issueLink.destinationObject.status.name in ["Story: Cancelled", "Tech Debt: Cancelled"])) {
		log.debug("${issueLink.destinationObject.getCustomFieldValue(customField).toString()}")
        def SP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
        totalSP += SP as Double;
       
        if(issueLink.destinationObject.status.name in ["Story: Done", "Tech Debt: Done", "DevOps: Done"]){
            def closedSP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
            totalClosedSP += closedSP as Double;
        }
    }
}

def epicDays = issue.getCustomFieldValue(customField2) ?: 0
def total = issue.status.name in ["Epic: Ready", "Epic: In Development", "Epic: Demo"]
	? totalSP
	: Math.max((double) totalSP, (double) epicDays);

return total - totalClosedSP;

// Epic Team

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.component.ComponentAccessor;

def componentManager = ComponentManager.getInstance()
def issueLinkManager = ComponentAccessor.getIssueLinkManager();
def cfManager = ComponentAccessor.getCustomFieldManager()

if(!(issue.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps"]))
{
    return null;
}
                                
def team = null;

customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Team");

issueLinkManager.getInwardLinks(issue.id)?.each {issueLink ->
    if (issueLink.issueLinkType.name == "Epic-Story Link"
       && issueLink.sourceObject.issueType.name == "Epic")
    {
        team = issueLink.sourceObject.getCustomFieldValue(customField);
    }
}

return team;

// Epic Progress

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.component.ComponentAccessor;
import java.text.DecimalFormat;

if(issue.issueType.name != "Epic"){
    return "";
}

def componentManager = ComponentManager.getInstance()
def issueLinkManager = ComponentAccessor.getIssueLinkManager();
def cfManager = ComponentAccessor.getCustomFieldManager()

Double totalSP = 0;
Double closedSP = 0;
def hasStories = false;

customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");

issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
    if (issueLink.issueLinkType.name == "Epic-Story Link" && 
       issueLink.destinationObject.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps"]
       && !(issueLink.destinationObject.status.name in ["Story: Cancelled", "Tech Debt: Cancelled"])){
        def sp = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0;
        totalSP += sp as Double;
    	if(issueLink.destinationObject.status.name in ["Story: Done", "Tech Debt: Done", "DevOps: Done"]){
            closedSP += sp as Double;
        }
    	hasStories = true;
    }
}

if(!hasStories){
    return "";
}
def progress = totalSP == 0 ? 0 : closedSP/totalSP;
def color = "black";
if((issue.status.name == "Epic: Done" && progress < 1.0)
  || (issue.status.name != "Epic: Done" && progress == 1.0)
  || (issue.status.name in ["Epic: New", "Epic: Decomposition", "Epic: Ready"] && progress > 0.0))
{
    color = "red";
}

def df = new DecimalFormat("##%");
String formattedPercent = "<font style='color:${color}'>${df.format(progress)}</font>";

return formattedPercent;

// Epic Total Estimate

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.component.ComponentAccessor;

def componentManager = ComponentManager.getInstance()
def issueLinkManager = ComponentAccessor.getIssueLinkManager();
def cfManager = ComponentAccessor.getCustomFieldManager()

if(issue.issueType.name != "Epic"){
    return null;
}

Double totalSP = 0

customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");
log.debug("${customField.id}")

def customField2 = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Estimate in Days");
log.debug("${customField.id}")

issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
    log.debug("Link of type ${issueLink.issueLinkType.name} from ${issueLink.sourceObject} to ${issueLink.destinationObject}")
    log.debug("${issueLink.destinationObject.issueType.name}")
    if (issueLink.issueLinkType.name == "Epic-Story Link" && 
       issueLink.destinationObject.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps"]
       && !(issueLink.destinationObject.status.name in ["Story: Cancelled", "Tech Debt: Cancelled"])) {
		log.debug("${issueLink.destinationObject.getCustomFieldValue(customField).toString()}")
        def SP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
        totalSP += SP as Double;
    }
}

def epicDays = issue.getCustomFieldValue(customField2) ?: 0
def total = issue.status.name in ["Epic: Ready", "Epic: In Development", "Epic: Demo", "Epic: Done"]
	? totalSP
	: Math.max((double) totalSP, (double) epicDays);

return total;

// End