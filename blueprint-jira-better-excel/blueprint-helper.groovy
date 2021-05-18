import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.issue.util.AggregateTimeTrackingCalculatorFactory;
import com.atlassian.jira.issue.util.AggregateTimeTrackingBean;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.history.ChangeItemBean;
import java.text.*;

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
			return searchResult.results;
		} 
		else { 
			// Log errors if invalid JQL is used so we can fix it
			log.error("Invalid JQL: " + jqlSearch);
			return null;
		}
	}
	
	public String getCollectionField(Issue issue, String name) {
		def field = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName(name)[0];
		def fieldValue = issue.getCustomFieldValue(field);
		return fieldValue?.collect { it.toString() }?.join(',');
	}
	
	public String getLabels(Issue issue) {
		def labels = issue.getLabels();
		return labels?.collect { it.label}?.join(',');
	}
	
	public Double getEpicProgress(Issue issue) {
		def fieldEpicProgress = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Epic Progress")[0];
		String fiedValue = issue.getCustomFieldValue(fieldEpicProgress);
		def percent = fiedValue?.replaceAll("<(.|\n)*?>|%", '')
		
		return percent ? Double.parseDouble(percent) / 100 : null;
	}
	
	public def getEpicLinkKey(Issue issue) {
		def field = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Epic Link")[0];
		def value = issue.getCustomFieldValue(field);
		return value;
	}
	

	public AggregateTimeTrackingBean getAggregates(Issue issue) {
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

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Story Points")[0];

		issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
			if (issueLink.issueLinkType.name == "Epic-Story Link" && 
			   ((issueLink.destinationObject.issueType.name == "Spike" && !(issueLink.destinationObject.status.name in ["Story: Cancelled"])) || (issueLink.destinationObject.issueType.name in ["Story", "Tech Debt", "DevOps"]
			   && !(issueLink.destinationObject.status.name in ["Story: New", "Story: Review", "Story: Cancelled",
																"Tech Debt: New", "Tech Debt: Review", "Tech Debt: Cancelled",
																"DevOps: New"])))) {
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

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Story Points")[0];

		issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
			if (issueLink.issueLinkType.name == "Epic-Story Link" && 
			   ((issueLink.destinationObject.issueType.name == "Story" && issueLink.destinationObject.status.name == "Story: Ready")
			   || (issueLink.destinationObject.issueType.name == "Spike" && issueLink.destinationObject.status.name == "Story: New")
			   || (issueLink.destinationObject.issueType.name == "Tech Debt" && issueLink.destinationObject.status.name == "Tech Debt: Ready")
			   || (issueLink.destinationObject.issueType.name == "DevOps" && issueLink.destinationObject.status.name in ["DevOps: Ready", "DevOps: On Hold"]))) {
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

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Story Points")[0];

		issueLinkManager.getOutwardLinks(issue.id)?.each {issueLink ->
			if (issueLink.issueLinkType.name == "Epic-Story Link" && 
				issueLink.destinationObject.status.name in ["Story: In Development", "Story: In Development - On Hold", "Tech Debt: In Development", "Tech Debt: In Development - On Hold", "DevOps: In Progress"]) {
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

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Story Points")[0];

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
	
	public String getLastCommitmentSprint(Issue issue)
	{
		if(!(issue.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps", "Bug"]))
		{
			return null;
		}
		
		String commitmentSprint = null;
		String release = "Austn";
		def labelTemplate = "Commitment." + release;
		def label = getLabels(issue);
		def i = 9; // the number of sprints
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
			release = "X-Ray";
			labelTemplate = "Commitment." + release;
			i = 6;
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
	
	public String getLastSprint(Issue issue)
	{
		if(!(issue.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps", "Bug"]))
		{
			return null;
		}
		
		String sprint = null;
		String release = "Belarus";
		def sprintTemplate = release + "-Sprint-";
		def sprints = getCollectionField(issue, "Sprint");
		def i = 9; // the number of sprints
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
			release = "Austin";
			sprintTemplate = release + "-Sprint-";
			i = 9;
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
		return getLastSprint(issue)?.replace("ustin", "")?.replace("-ray", "");
	}
	
	
	public def getCycleTime(Issue issue)
	{
		def startStatuses = ["story: in development", "tech debt: in development", "devops: in progress"];
		def endStatuses = ["story: done", "tech debt: done", "devops: done"];
		return getTimeBetweenStates(issue, startStatuses, endStatuses, true);
	}
	
	public def getDevelopmentTime(Issue issue)
	{
		if(!(issue.issueType.name.toLowerCase() in ["story", "tech debt", "devops"]))
			return null;
		
		def startStatuses = ["story: in development", "tech debt: in development", "devops: in progress"];
		def endStatuses = ["story: ready for validation", "tech debt: ready for validation", "devops: done"];
		return getTimeBetweenStates(issue, startStatuses, endStatuses, true);
	}
	
	public def isStoryDecopmosed(Issue issue)
	{
		if(!(issue.issueType.name.toLowerCase() in ["story", "spike"]))
		{
			return null;
		}
		
		def status = issue?.status?.name?.toLowerCase();
		if(issue.issueType.name.toLowerCase() in ["spike"])
		{
			return !(status in ["spike: cancel"]) ? "Yes" : "No";
		}
		else if(issue.issueType.name.toLowerCase() in ["story"])
		{
			return !(status in ["story: cancelled", "story: decomposition", "story: new", "story: pm review", "story: qa review", "story: review"]) ? "Yes" : "No";
		}
		
		return null;
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
	
	public int getTimeBetweenStates(Issue issue, def startStatuses, def endStatuses, boolean isStartStatusFirst)
	{	
		def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()

		def doneStatuses = ["story: done", "tech debt: done", "devops: done"]

		if(!(issue.status.name.toLowerCase() in doneStatuses))
			return null

		Date startDate = null
		Date endDate = null
		
		def df = new SimpleDateFormat("dd.MM.yyy");
		def holidays = [
			df.parse("30.12.2020"), // Office Holiday
			df.parse("31.12.2020"), // Office Holiday
			df.parse("01.01.2021"), // New Year
			df.parse("15.02.2021"), // Family Day
			df.parse("02.04.2021"), // Good Friday
			df.parse("24.05.2021"), // Victoria Day
			df.parse("01.07.2021"), // Canada Day
			df.parse("02.08.2021"), // Civic Holiday
			df.parse("06.09.2021"), // Labour Day
			df.parse("11.10.2021"), // Thanksgiving
			df.parse("27.12.2021"), // Christmas Day
			df.parse("28.12.2021"), // Boxing Day
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
		def status = issue?.status?.name?.toLowerCase();
		if(status == null)
		{
			return null;
		}
		def newStatuses = ["epic: new", "epic: decomposition", "epic: review",
			"story: prototype done", "story: new", "story: review",
			"tech debt: prototype done", "tech debt: new", "tech debt: review",
			"devops: new", "devops: on hold",
			"bug: new", "bug: new - on hold", "bug: triage", "bug: investigate"];
		def readyStatuses = ["epic: ready", "story: ready", "tech debt: ready", "devops: ready", "bug: ready"];
		def inDevStatuses = ["epic: in development",
			"story: in development", "story: in development - on hold",
			"tech debt: in development", "tech debt: in development - on hold",
			"devops: in progress",
			"bug: in development", "bug: in development - on hold"];
		def validationStatuses = ["epic: in validation",
			"story: ready for validation", "story: validation", "story: validation - on hold", "story: demo checkpoint", "story: demo checkpoint - on hold",
			"tech debt: ready for validation", "tech debt: validation", "tech debt: validation - on hold", "tech debt: demo checkpoint", "tech debt: demo checkpoint - on hold",
			"bug: validation", "bug: validation - on hold"];
		def closedStatuses = ["epic: done", "story: done", "tech debt: done", "devops: done", "bug: closed"];
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
	
	public def isInBacklogHealth(Issue issue)
	{
		def type = issue?.issueType?.name?.toLowerCase();
		def status = issue?.status?.name?.toLowerCase();
		if((status in ["story: ready", "tech debt: ready", "devops: ready"])
			|| (type == "spike" && status == "story: new"))
			return "Yes";
		
		return null;
	}
	
	public def getBugToTriageCount() {
		return searchIssues('project = Storyteller AND issuetype in (Bug) AND status = "Bug: Triage"').size();
	}
	
	public def getCustomP0P1BugCount() {
		return searchIssues('project = Storyteller AND issuetype in (Bug) AND status not in ("Bug: Closed") AND Customer is not EMPTY AND priority in (Highest, High)').size();
	}
	public def getTotalP0P1BugCount() {
		return searchIssues('project = Storyteller AND issuetype in (Bug) AND status not in ("Bug: Closed") AND priority in (Highest, High)').size();
	}
	
	public def getReleaseComponent(Issue issue) {
		if(!(issue.issueType.name in ["Epic", "Story", "Spike", "Tech Debt", "DevOps"]))
		{
			return null;
		}
		
		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("ST:Components")[0];
		def fieldValue = issue.getCustomFieldValue(customField)?.toString();
		
		def customFieldRelease = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Active in Release #")[0];
		def fieldValueRelease = issue.getCustomFieldValue(customFieldRelease)?.toString();
		
		def customFieldEpicLink = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("Epic Link")[0];
		def fieldValueEpicLink = issue.getCustomFieldValue(customFieldEpicLink)?.toString();
		// 12.3
		/*if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25566")
		{
			return "Export COM to UiPath";
		}*/
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25283") //
		{
			return "COM Parameter Editor";
		}
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25567") //
		{
			return "Import Wizard - Blue Prism"; // Import Wizard (aka. Selective Import) - UiPath, Blue Prism
		}
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25805") //
		{
			return "Import Wizard - UiPath"; // NEW
		}
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25618") //
		{
			return "Intelligent Processes";
		}
		/*if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25617")
		{
			return "RPA Variables";
		}
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25522")
		{
			return "Update UiPath import with/without templates";
		}*/
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25660") //
		{
			return "RPA Metrics Dashboard";
		}
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25479") //
		{
			return "UX Enhancements for 12.3";
		}
		/*if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25636")
		{
			return "Import Blue Prism into COM";
		}*/
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25671") //
		{
			return "Applications Reporting Dashboard"; // Applications in Impact
		}
		
		if(fieldValueRelease == "12.3" && fieldValueEpicLink == "STOR-25806") //
		{
			return "RPA Variables"; // NEW
		}
		// 12.2
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25080")
		{
			return "UX Enhancements for 12.3";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25192")
		{
			return "Investigate Upgrade to Latest Windward";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25081")
		{
			return "Reversing AA v11: Clean Up Import";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25251")
		{
			return "Add Username to Pendo Visitor Name";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25249")
		{
			return "Toggle - Deleting DGB Doesn't Free License";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25084")
		{
			return "Import AA v11 into COM";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25083")
		{
			return "Import UiPath into COM";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25085")
		{
			return "Export COM to Blue Prism";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25087")
		{
			return "Populate COM with DGB Actions";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-25086")
		{
			return "COM Mapping for Most-Used Application Types";
		}
		if(fieldValueRelease == "12.2" && fieldValueEpicLink == "STOR-24654")
		{
			return "Import from Word MVP";
		}
		// 12.1
		if(fieldValueRelease == "12.1" && fieldValueEpicLink == "STOR-24653")
		{
			return "Common Object Model for RPA";
		}
		
		if(fieldValueRelease == "12.1" && fieldValueEpicLink in ["STOR-24753", "STOR-24889"])
		{
			return "UX Overhaul Tech Design";
		}
		if(fieldValueRelease == "12.1" && fieldValueEpicLink == "STOR-24465")
		{
			return "[POC] [Capgem] - UiPath to Blue Prism Converter (Not Synced)";
		}
		if(fieldValueRelease == "12.1" && fieldValueEpicLink == "STOR-24794")
		{
			return "AA11 to BP6.8 Converter MVP (Not Synced)";
		}
		if(fieldValueRelease == "12.1" && fieldValueEpicLink == "STOR-25061")
		{
			return "Reversing Automation Anywhere v11 V2: ZIP/AAPKG Import & Project Structure";
		}
		if(fieldValueRelease == "12.1" && fieldValueEpicLink == "STOR-24163")
		{
			return "Excel Update MVP";
		}
		if(fieldValueRelease == "12.1" && fieldValueEpicLink == "STOR-25052")
		{
			return "Reversing UiPath V1";
		}
	
		// R&D Bucket
		if(fieldValue in ["Platform", "Tech Debt", "Technical", "Release Management"])
		{
			return "R&D Bucket";
		}
		
		return "Other";
	}
	
	public def getDevOpsType(Issue issue) {
		if(issue.issueType.name != "DevOps"){
			return null;
		}

		def customField = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectsByName("DevOps Task Type")[0];
		return issue.getCustomFieldValue(customField);
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
