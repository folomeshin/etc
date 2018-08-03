import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.issue.util.AggregateTimeTrackingCalculatorFactory;
import com.atlassian.jira.issue.util.AggregateTimeTrackingBean;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.history.ChangeItemBean;
import java.text.*;

//filename = "Pegasus Release Planning - ${new Date().format('yyyy-MM-dd')}.xlsx".toString()
filename = "Pegasus Release Planning - ${new Date().format("yyyy-MM-dd-HH-mm-ss-z", TimeZone.getTimeZone('EST'))}.xlsx".toString();

bpHelper = new BlueprintHelper();
//issues = bpHelper.searchIssues('project = Storyteller AND issuetype in (Epic, Story, Spike, "Tech Debt", Bug) AND fixVersion in (Pegasus, Quasar) AND status not in ("Epic: Cancelled", "Story: Cancelled", "Tech Debt: Cancelled") AND NOT (issuetype = Epic AND (ST:Components = DevOps OR Team = TechComm))');

public class BlueprintHelper {
	public List<Issue> searchIssues(String jqlSearch) {
		SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
		def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();

		// Set the default list of issues to null just to be safe 
		//List<Issue> issues = null;

		// Perform the search as a user and return the result so it can be validated. 
		SearchService.ParseResult parseResult = searchService.parseQuery(user, jqlSearch);

		if (parseResult.isValid()) {

			def searchResult = searchService.search(user, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
			return searchResult.issues
		} 
		else { 
			// Log errors if invalid JQL is used so we can fix it
			log.error("Invalid JQL: " + jqlSearch);
			return null;
		}
	}
	
	public String getCollectionField(Issue issue, String name) {
		def field = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName(name);
		def fieldValue = issue.getCustomFieldValue(field);
		return fieldValue?.collect { it.name }?.join(',');
	}
	
	public String getLabels(Issue issue) {
		def labels = issue.getLabels();
		return labels?.collect { it.label}?.join(',');
	}
	
	public Double getEpicProgress(Issue issue) {
		def fieldEpicProgress = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Epic Progress");
		String fiedValue = issue.getCustomFieldValue(fieldEpicProgress);
		def percent = fiedValue?.replaceAll("<(.|\n)*?>|%", '')
		
		return percent ? Double.parseDouble(percent) / 100 : null;
	}
	
	public def getEpicLinkKey(Issue issue) {
		def field = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Epic Link");
		def value = issue.getCustomFieldValue(field);
		return value;
	}
	

	public def getAggregates(Issue issue) {
		def timeTrackingCalculatorFactory = ComponentAccessor.getOSGiComponentInstanceOfType(AggregateTimeTrackingCalculatorFactory.class)
		def calculator = timeTrackingCalculatorFactory.getCalculator(issue);
		return calculator.getAggregates(issue);
	}
	
	public def getAggregateTimeSpentInHours(Issue issue) {
		def timeSpent = getAggregates(issue).timeSpent;
		return timeSpent ? timeSpent/3600.0 : null;
	}
	
	public def getAggregateOriginalEstimateInHours(Issue issue) {
		def originalEstimate = getAggregates(issue).originalEstimate;
		return originalEstimate ? originalEstimate/3600.0 : null;
	}
	
	public def getAggregateRemainingEstimateInHours(Issue issue) {
		def remainingEstimate = getAggregates(issue).remainingEstimate;
		return remainingEstimate ? remainingEstimate/3600.0 : null;
	}
	
	public def getGroupedTeam(String team) {
		if(!team?.trim()) {
			return "Unassigned";
		}
		return team in ["No Pasaran", "Evolution", "Status200"] ? "SoftTeco" : team;
	}
	
	public def getDecomposedEstimate(Issue issue) {
		
		def issueLinkManager = ComponentAccessor.getIssueLinkManager();
		def cfManager = ComponentAccessor.getCustomFieldManager()

		if(issue.issueType.name != "Epic"){
			return null;
		}

		Double decomposedSP = 0;

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");

		issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
			if (issueLink.issueLinkType.name == "Epic-Story Link" && 
			   ((issueLink.destinationObject.issueType.name == "Spike" && !(issueLink.destinationObject.status.name in ["Story: Cancelled"])) || (issueLink.destinationObject.issueType.name in ["Story", "Tech Debt", "DevOps"]
			   && !(issueLink.destinationObject.status.name in ["Story: New", "Story: Review", "Story: Cancelled",
																"Tech Debt: New", "Tech Debt: Review", "Tech Debt: Cancelled",
																"DevOps: New", "Backlog"])))) {
				def SP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
				decomposedSP += SP as Double;
			}
		}

		return decomposedSP == 0 ? null : decomposedSP;
	}
	
	public def getReadyEstimate(Issue issue) {
		
		def issueLinkManager = ComponentAccessor.getIssueLinkManager();
		def cfManager = ComponentAccessor.getCustomFieldManager()

		if(issue.issueType.name != "Epic"){
			return null;
		}

		Double readySP = 0;

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");

		issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
			if (issueLink.issueLinkType.name == "Epic-Story Link" && 
			   ((issueLink.destinationObject.issueType.name == "Story" && issueLink.destinationObject.status.name == "Story: Ready")
			   || (issueLink.destinationObject.issueType.name == "Spike" && issueLink.destinationObject.status.name == "Story: New")
			   || (issueLink.destinationObject.issueType.name == "Tech Debt" && issueLink.destinationObject.status.name == "Tech Debt: Ready")
			   || (issueLink.destinationObject.issueType.name == "DevOps" && issueLink.destinationObject.status.name == "DevOps: Ready"))) {
				def SP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
				readySP += SP as Double;
			}
		}

		return readySP == 0 ? null : readySP;
	}
	
	public def getInDevEstimate(Issue issue) {
		
		def issueLinkManager = ComponentAccessor.getIssueLinkManager();
		def cfManager = ComponentAccessor.getCustomFieldManager()

		if(issue.issueType.name != "Epic"){
			return null;
		}

		Double readySP = 0;

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");

		issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
			if (issueLink.issueLinkType.name == "Epic-Story Link" && 
				issueLink.destinationObject.status.name in ["Story: In Development", "Story: In Development - On Hold", "Tech Debt: In Development", "Tech Debt: In Development - On Hold"]) {
				def SP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
				readySP += SP as Double;
			}
		}

		return readySP == 0 ? null : readySP;
	}
	
	public def getValidationEstimate(Issue issue) {
		
		def issueLinkManager = ComponentAccessor.getIssueLinkManager();
		def cfManager = ComponentAccessor.getCustomFieldManager()

		if(issue.issueType.name != "Epic"){
			return null;
		}

		Double readySP = 0;

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Story Points");

		issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
			if (issueLink.issueLinkType.name == "Epic-Story Link" && 
				issueLink.destinationObject.status.name in ["Story: Ready for Validation", "Story: Validation", "Story: Validation - On Hold", "Story: Demo Checkpoint", "Story: Demo Checkpoint - On Hold",
					"Tech Debt: Ready for Validation", "Tech Debt: Validation", "Tech Debt: Validation - On Hold", "Tech Debt: Demo Checkpoint", "Tech Debt: Demo Checkpoint - On Hold"]) {
				def SP = issueLink.destinationObject.getCustomFieldValue(customField) ?: 0
				readySP += SP as Double;
			}
		}

		return readySP == 0 ? null : readySP;
	}
	
	public def getLastCommitmentSprint(Issue issue)
	{
		if(!(issue.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps", "Bug"]))
		{
			return null;
		}
		
		String commitmentSprint = null;
		String release = "Quasar";
		def labelTemplate = "Commitment." + release;
		def label = getLabels(issue);
		def i = 14; // the number of sprints
		while(i > 0)
		{
			if(label.contains(labelTemplate + i))
			{
				commitmentSprint = release + i;
				break;
			}
			i--;
		}
		if(commitmentSprint == null)
		{
			release = "Pegasus";
			labelTemplate = "Commitment." + release;
			i = 8;
			while(i > 0)
			{
				if(label.contains(labelTemplate + i))
				{
					commitmentSprint = release + i;
					break;
				}
				i--;
			}
		}
		
		return commitmentSprint;
	}
	
	public def getLastSprint(Issue issue)
	{
		if(!(issue.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps", "Bug"]))
		{
			return null;
		}
		
		String sprint = null;
		String release = "Quasar";
		def sprintTemplate = release + "-Sprint-";
		def sprints = getCollectionField(issue, "Sprint");
		def i = 14; // the number of sprints
		while(i > 0)
		{
			if(sprints.contains(sprintTemplate + i))
			{
				sprint = release + i;
				break;
			}
			i--;
		}
		if(sprint == null)
		{
			release = "Pegasus";
			sprintTemplate = release + "-Sprint-";
			i = 8;
			while(i > 0)
			{
				if(sprints.contains(sprintTemplate + i))
				{
					sprint = release + i;
					break;
				}
				i--;
			}
		}
		
		return sprint;
	}
	
	public def getLastSprintLabel(Issue issue)
	{
		return getLastSprint(issue)?.replace("uasar", "")?.replace("egasus", "");
	}
	
	
	public def getCycleTime(Issue issue)
	{
		def startStatuses = ["story: in development", "tech debt: in development", "devops: in progress"];
		def endStatuses = ["story: done", "tech debt: done", "devops: done"];
		return getTimeBetweenStates(issue, startStatuses, endStatuses, true);
	}
	
	public def getDevelopmentTime(Issue issue)
	{
		if(!(issue.issueType.name.toLowerCase() in ["story", "tech debt"]))
			return null;
		
		def startStatuses = ["story: in development", "tech debt: in development"];
		def endStatuses = ["story: ready for validation", "tech debt: ready for validation"];
		return getTimeBetweenStates(issue, startStatuses, endStatuses, true);
	}
	
	public def getReadyForValidationTime(Issue issue)
	{
		if(!(issue.issueType.name.toLowerCase() in ["story", "tech debt"]))
			return null;
		
		def startStatuses = ["story: ready for validation", "tech debt: ready for validation"];
		def endStatuses = ["story: validation", "tech debt: validation"];
		return getTimeBetweenStates(issue, startStatuses, endStatuses, false) - 1;
	}
	
	public def getValidationTime(Issue issue)
	{
		if(!(issue.issueType.name.toLowerCase() in ["story", "tech debt"]))
			return null;
		
		def startStatuses = ["story: validation", "tech debt: validation"];
		def endStatuses = ["story: done", "tech debt: done"];
		return getTimeBetweenStates(issue, startStatuses, endStatuses, false) - 1;
	}
	
	public def getTimeBetweenStates(Issue issue, def startStatuses, def endStatuses, boolean isStartStatusFirst)
	{	
		def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()

		def doneStatuses = ["story: done", "tech debt: done", "devops: done"]

		if(!(issue.status.name.toLowerCase() in doneStatuses))
			return null

		Date startDate = null
		Date endDate = null
		
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
		];
		
		changeHistoryManager.getChangeItemsForField(issue, "status").each {ChangeItemBean item ->
			if(item.toString.toLowerCase() in startStatuses && (startDate == null || (isStartStatusFirst ? item.created < startDate : item.created > startDate)))
				startDate = new Date(item.created.getTime()).clearTime()
			if(item.toString.toLowerCase() in endStatuses && (endDate == null || item.created > endDate))
				endDate = new Date(item.created.getTime()).clearTime()
		}
	
		//---------------------------------------------------------------------------------------------------
		
		def span = endDate - startDate
		def businessDays = span + 1
		int fullWeekCount = businessDays / 7 as int
		// find out if there are weekends during the time exceeding the full weeks
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

		return businessDays == 0 ? 1 : businessDays; // businessDays = 0 if it happens on weekend or holiday
	}
	
	// The state is just a grouping of the status
	public String getState(Issue issue) {
		def status = issue?.statusObject?.name?.toLowerCase();
		if(status == null)
		{
			return null;
		}
		def newStatuses = ["epic: new", "epic: decomposition", "epic: review",
			"story: prototype done", "story: new", "story: review",
			"tech debt: prototype done", "tech debt: new", "tech debt: review",
			"bug: new", "bug: new - on hold", "bug: triage", "bug: investigate"];
		def readyStatuses = ["epic: ready", "story: ready", "tech debt: ready", "bug: ready"];
		def inDevStatuses = ["epic: in development",
			"story: in development", "story: in development - on hold",
			"tech debt: in development", "tech debt: in development - on hold",
			"bug: in development", "bug: in development - on hold"];
		def validationStatuses = ["epic: demo",
			"story: ready for validation", "story: validation", "story: validation - on hold", "story: demo checkpoint", "story: demo checkpoint - on hold",
			"tech debt: ready for validation", "tech debt: validation", "tech debt: validation - on hold", "tech debt: demo checkpoint", "tech debt: demo checkpoint - on hold",
			"bug: validation", "bug: validation - on hold"];
		def closedStatuses = ["epic: done", "story: done", "tech debt: done", "bug: closed"];
		def cancelledStatuses = ["epic: cancelled", "story: cancelled", "tech debt: cancelled"];
		
		if(status in newStatuses)
		{
			return "New";
		}
		else if(status in readyStatuses)
		{
			return "Ready";
		}
		else if(status in inDevStatuses)
		{
			return "In Dev";
		}
		else if(status in validationStatuses)
		{
			return "Validation";
		}
		else if(status in closedStatuses)
		{
			return "Closed";
		}
		else if(status in cancelledStatuses)
		{
			return "Cancelled";
		}
		
		return "Unknown";
	}
	
	public def getBugToTriageCount() {
		return searchIssues('project = Storyteller AND issuetype in (Bug) AND status = "Bug: Triage"').size();
	}
	
	public def getQuasarComponent(Issue issue) {
		if(!(issue.issueType.name in ["Epic", "Story", "Spike", "Tech Debt"]))
		{
			return null;
		}
		
		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("ST:Components");
		def fieldValue = issue.getCustomFieldValue(customField)?.toString();
		
		if(fieldValue == "Diagram Editor")
		{
			return "UME";
		}
		else if(fieldValue == "Artifact List")
		{
			return "Artifact List v2";
		}
		else if(fieldValue in ["Platform", "Tech Debt", "Technical", "Admin", "Release Management", "R&D DevOps"])
		{
			return "R&D Bucket";
		}
		
		return "Other";
	}
	
	public def getToday() {
		return (new Date()).format("M/d/yyyy", TimeZone.getTimeZone("EST"));
	}
	
	// For QA Dashboard
	
	public String getQaType(Issue issue) {
		if(issue.summary?.toLowerCase().indexOf("[qa]") > -1) {
			return "QA Only";
		}
		if(getLabels(issue)?.indexOf("T.ManualTesting") > -1) {
			return "QA Manual";
		}
		return "No QA Manual";
	}
	
	public String getQaOnlyAppType(Issue issue) {
		if(!(getQaType(issue) in ["QA Only"])) {
			return "";
		}
		
		boolean isSt = issue.summary?.indexOf("ST") > -1;
		boolean isSl = issue.summary?.indexOf("SL") > -1;
		
		if(isSt && isSl) {
			return "ST-SL";
		}
		if(isSt) {
			return "ST";
		}
		if(isSl) {
			return "SL";
		}
		return "Other";
	}
}
