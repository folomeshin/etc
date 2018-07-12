import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.issue.MutableIssue;
//import com.atlassian.greenhopper.service.sprint.Sprint
import com.atlassian.jira.issue.util.AggregateTimeTrackingCalculatorFactory;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.issue.util.AggregateTimeTrackingBean;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.history.ChangeItemBean;
import com.atlassian.jira.issue.changehistory.ChangeHistory;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.Date.*;
import groovy.time.TimeCategory;

filename = "Hack Day 6 (Sprint Burn-down) - ${new Date().format("yyyy-MM-dd-HH-mm-ss-z", TimeZone.getTimeZone('EST'))}.xlsx".toString();

bpHelper2 = new BlueprintHelper2();
/* if(bpHelper2?.log && log) {
	bpHelper2.log = log;
} */
bpHelper2.log = new Logger();

//burndownChartModel3 = bpHelper2.getBurndownChartModel("Quasar-Sprint-6");
burndownChartModel4 = bpHelper2.getBurndownChartModel("Quasar-Sprint-8");

//log?.warn(bpHelper2.log.log.join("\n"));

public class BlueprintHelper2 {

	public def log = null;

	public List<BurndownChartModelItem> getBurndownChartModel(String sprint)
	{
		//def model = new ArrayList<BurndownChartModelItem>();
		
		String release = sprint.substring(0,sprint.indexOf("-"));
		log?.warn("Sprint: ${sprint}, Release: ${release}");
		
		Date currentDate = new Date();
		log?.warn("Current Date: ${currentDate}");
		
		def jqlSearch = "project = Storyteller AND issuetype in (Story, Spike, 'Tech Debt', Bug) AND status not in ('Story: Cancelled', 'Story: Done', 'Tech Debt: Cancelled', 'Tech Debt: Done', 'Bug: Closed') AND Sprint in (${sprint}) AND fixVersion in (${release})";
		def issues = searchIssues(jqlSearch);
		
		Tuple startAndEndDates = getSprintStartAndEndDates(issues.first(), sprint);
		log?.warn("Start Date: ${startAndEndDates[0]}, End Date: ${startAndEndDates[1]}");
		
		def liteIssues = new ArrayList<LiteIssue>();
		issues?.each{Issue issue -> 
			liteIssues.add(getLiteIssue(issue));
		}
		
		liteIssues.removeAll{it.sprint != sprint};
		log?.warn("# of issues in sprint '${sprint}' - ${liteIssues.size()}");
		
		Map<String,LiteIssue> mapIssues = liteIssues.collectEntries{[it.key,it]}
		log?.warn("# of issues in issue map - ${mapIssues.size()}");
		
		Double currentSP = liteIssues.sum({it.storyPoints});
		log?.warn("Current SP: ${currentSP}");
		
		// Process history of issues changed after the sprint started.
		def changedIssues = getIssuesChangedAfter(startAndEndDates[0]);
		log?.warn("# of changed issues - ${changedIssues.size()}");
		changedIssues.each {Issue issue ->
			if(!mapIssues[issue.key]) {
				mapIssues[issue.key] = getLiteIssue(issue);
			}
		}
		
		def changeHistories = ProcessChangeHistoryForSprintUpdate(changedIssues, startAndEndDates[0]).sort{it.getTimePerformed()};
		Collections.reverse(changeHistories);
		log?.warn("# of change histories - ${changeHistories.size()}");
		
		def model = getChangeModel(changeHistories, mapIssues, currentSP, currentDate, sprint, release);
		
		model.sort{it.date};
		
		if(currentDate < startAndEndDates[1])
		{
			model.add(new BurndownChartModelItem(startAndEndDates[1], null, null));
		}
		else
		{
			model.removeAll {it.date >= startAndEndDates[1] || it.date < startAndEndDates[0]};
			model.add(new BurndownChartModelItem(startAndEndDates[1], model.last()?.actual, null));
		}
		
		log?.warn("Start Date: ${startAndEndDates[0]} ++++++ End Date: ${startAndEndDates[1]} ++++++ First Date: ${model[0].date})");
		if(model[0].date > startAndEndDates[0])
		{
			log?.warn("+++++ Added Start Date");
			model.add(0, new BurndownChartModelItem(startAndEndDates[0], model[0].actual, null));
		}
		
		
		// Insert items for vertical transitions
		for(int i = model.size() - (currentDate < startAndEndDates[1] ? 3 : 2); i >= 0; i--) {
			model.add(i + 1, new BurndownChartModelItem(model[i].date, model[i + 1].actual, null));
		}
		
		model.each {def issue ->
			use( TimeCategory ) {
				issue.date = issue.date - 4.hours;
			}

			//log?.warn("Model Record: ${issue.date} - ${issue.actual} (${issue.ideal})");
		}
		
		return model;
	}
	
	private Double getSprintRemainingSp(Collection<LiteIssue> issues, String sprint, String release)
	{
		Double sp = 0;
		issues.each {LiteIssue issue ->
			if(issue.sprint == sprint && issue.release?.indexOf(release) > -1 && !(issue.status in ['Story: Cancelled', 'Story: Done', 'Tech Debt: Cancelled', 'Tech Debt: Done', 'Bug: Closed'])) {
				sp +=issue.storyPoints;
			}
		}
		return sp;
	}
	
	private List<BurndownChartModelItem> getChangeModel(List<ChangeHistory> changeHistories, Map<String,LiteIssue> mapIssues,
																Double currentSP, Date currentDate, String sprint, String release)
	{
		def sp = currentSP ?: 0;
		def model = new ArrayList<BurndownChartModelItem>();
		model.add(new BurndownChartModelItem(currentDate, currentSP, null));
		
		changeHistories.each {ChangeHistory ch ->
			def oldIssue = mapIssues[ch.issue.key];
			if(oldIssue == null) {
				oldIssue = getLiteIssue(ch.issue);
				mapIssues[oldIssue.key] = oldIssue;
			}
			def updatedIssue = getUpdatedLiteIssue(oldIssue, ch)
			mapIssues[updatedIssue.key] = updatedIssue;
			
			def sprintSpChange = getSprintSpChange(sprint, release, oldIssue, updatedIssue);
			log?.warn("      ??????? Sprint Change = ${sprintSpChange}")
			
			if(sprintSpChange != null && sprintSpChange != 0) {
				sp += sprintSpChange;
				// Temp ideal  assignment
				//model.add(new BurndownChartModelItem(ch.getTimePerformed(), sp, getSprintRemainingSp(mapIssues.values(), sprint)));
				Double sprintRemainingSp = getSprintRemainingSp(mapIssues.values(), sprint, release);
				model.add(new BurndownChartModelItem(ch.getTimePerformed(), sprintRemainingSp, sp));
				log?.warn("      +++++++ Sprint Remaining SP = ${sprintRemainingSp}")
			}
		}
		
		return model;
	}
	
	/*private Double getSprintSpChange(String sprint, LiteIssue oldIssue, LiteIssue updatedIssue){
		def closedStatuses = ["Story: Cancelled", "Story: Done", "Tech Debt: Cancelled", "Tech Debt: Done", "Bug: Closed"];
		
		if((sprint != oldIssue.sprint && sprint != updatedIssue.sprint)
			|| (oldIssue.release?.indexOf(release) < 0 && updatedIssue.release?.indexOf(release) < 0)
			||(oldIssue.status in closedStatuses && updatedIssue.status in closedStatuses)) {
			return 0;
		}
		
		if(oldIssue.status in closedStatuses && !(updatedIssue.status in closedStatuses) && sprint == updatedIssue.sprint)
		{
			return updatedIssue.storyPoints;
		}
		
		if(!(oldIssue.status in closedStatuses) && updatedIssue.status in closedStatuses && sprint == updatedIssue.sprint)
		{
			return -updatedIssue.storyPoints;
		}
		
		if(!(oldIssue.status in closedStatuses) && !(updatedIssue.status in closedStatuses) && sprint == updatedIssue.sprint && sprint == oldIssue.sprint)
		{
			return (updatedIssue.storyPoints - oldIssue.storyPoints);
		}
		
		if(!(oldIssue.status in closedStatuses) && sprint != updatedIssue.sprint && sprint == oldIssue.sprint)
		{
			return -oldIssue.storyPoints;
		}
		
		if(!(oldIssue.status in closedStatuses) && sprint == updatedIssue.sprint && sprint != oldIssue.sprint)
		{
			return updatedIssue.storyPoints;
		}
		
		return 0;
	}*/
	
	private Double getSprintSpChange(String sprint, String release, LiteIssue oldIssue, LiteIssue updatedIssue){
		
		if(!isIssueOpenInSprint(oldIssue, sprint, release) && !isIssueOpenInSprint(updatedIssue, sprint, release)) {
			return 0;
		}
		
		if(isIssueOpenInSprint(oldIssue, sprint, release) && !isIssueOpenInSprint(updatedIssue, sprint, release))
		{
			return -updatedIssue.storyPoints;
		}
		
		if(!isIssueOpenInSprint(oldIssue, sprint, release) && isIssueOpenInSprint(updatedIssue, sprint, release))
		{
			return updatedIssue.storyPoints;
		}
		
		if(isIssueOpenInSprint(oldIssue, sprint, release) && isIssueOpenInSprint(updatedIssue, sprint, release))
		{
			return (updatedIssue.storyPoints - oldIssue.storyPoints);
		}
		
		return 0;
	}
	
	private boolean isIssueOpenInSprint(LiteIssue issue, String sprint, String release) {
		def closedStatuses = ["Story: Cancelled", "Story: Done", "Tech Debt: Cancelled", "Tech Debt: Done", "Bug: Closed"];
		return !(issue.status in closedStatuses) && sprint == issue.sprint && issue.release?.indexOf(release) > -1;
	}
	
	private LiteIssue getUpdatedLiteIssue(LiteIssue oldIssue, ChangeHistory changeHistory) {
		
		def updatedIssue = new LiteIssue(oldIssue.key, oldIssue.sprint, oldIssue.release, oldIssue.status, oldIssue.storyPoints);
		
		changeHistory.getChangeItemBeans().each {ChangeItemBean item ->
			if(item.getField().toLowerCase() == "sprint") {
				updatedIssue.sprint = getExactLastSprintFromString(item.fromString);
			} else if(item.getField().toLowerCase() == "status") {
				updatedIssue.status = item.fromString;
			} else if(item.getField().toLowerCase() == "story points") {
				//log?.warn("??????? ${item?.toString}")
				updatedIssue.storyPoints = item?.fromString == null || item.fromString.isEmpty() ? 0 : Double.valueOf(item.fromString);
			}
			log?.warn("Issue = '${oldIssue.key}','${oldIssue.sprint}' <--- '${updatedIssue.sprint}','${oldIssue.status}' <--- '${updatedIssue.status}','${oldIssue.storyPoints}' <--- '${updatedIssue.storyPoints}' === FIELD='${item.getField()}':::: '${item.fromString}' --> '${item.toString}' ${item.created} (${item.created.getTime()})");
		}
		
		return updatedIssue;
	}
	
	private List<Issue> searchIssues(String jqlSearch) {
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
			log?.error("Invalid JQL: " + jqlSearch);
			return null;
		}
	}
	
	private LiteIssue getLiteIssue(Issue issue) {
		String key = issue.key;
		String liteSprint = getExactLastSprint(issue);//getFieldValue(issue, "Sprint");
		String release = issue.fixVersions.name;
		String status = issue.status?.name;
		Double storyPoints = getFieldValue(issue, "Story Points");
		//log?.warn("!!!!! NEW LITE ISSUE !!!!!: key='${key}', sprint='${liteSprint}', release='${release}', status='${status}', SP='${storyPoints}'");
		return new LiteIssue(key, liteSprint, release, status, storyPoints);
	}
	
	private def getFieldValue(Issue issue, String fieldName)
	{
		def customFieldManager = ComponentAccessor.getCustomFieldManager();
		def field = customFieldManager.getCustomFieldObjectByName(fieldName);
		def fieldValue = issue.getCustomFieldValue(field);
		
		if(fieldValue == null) {
			return null;
		}
		else if(!(fieldValue instanceof Collection)) {
			return fieldValue;
		}
		else if(fieldValue.size() == 1) {
			return fieldValue.first().name;
		}
		
		String value = "";
		fieldValue.each{def fv ->
			value += ",${fv.name}";
		}
		return value;
	}
	
	private Tuple getSprintStartAndEndDates(Issue issue, String sprint)
	{
		def customFieldManager = ComponentAccessor.getCustomFieldManager();
		def field = customFieldManager.getCustomFieldObjectByName("Sprint");
		def fieldValue = issue.getCustomFieldValue(field);
		
		if(fieldValue == null) {
			return null;
		}
		
		Tuple tuple = null;
		fieldValue.each{def fv ->
			if(fv?.name.indexOf(sprint) > -1)
			{
				log?.warn("Sprint Object: ${fv}")
				Date startDate = fv?.startDate?.toDate();
				Date endDate = fv?.completeDate?.toDate() ?: fv?.endDate?.toDate();
				tuple = new Tuple(startDate, endDate);
				return;
			}
		}
		return tuple;
	}
	
	private List<Issue> getIssuesChangedAfter(Date afterDate)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd HH:mm");
		String after = sdf.format(afterDate);
		def jqlSearch = "project = Storyteller AND issuetype in (Story, Spike, 'Tech Debt', Bug) AND updatedDate > '${after}'";
		log?.warn("filter for changed issues: ${jqlSearch}");
		return searchIssues(jqlSearch);
	}
	
	private List<ChangeHistory> ProcessChangeHistoryForSprintUpdate(List<Issue> issues, Date since)
	{
		List<ChangeHistory> list = new ArrayList<ChangeHistory>();
	
		def componentManager = ComponentManager.getInstance();
		def changeHistoryManager = ComponentAccessor.getChangeHistoryManager();
		Date sinceMinus;
		use (TimeCategory) {
			sinceMinus = since - 10.milliseconds;
		}

		issues.each {Issue issue ->
			changeHistoryManager.getChangeHistoriesSince(issue, sinceMinus).each {ChangeHistory ch ->
				//log?.warn("'${ch.getAuthorDisplayName()}' ------------> '${ch.getTimePerformed()}'");
				def isChange = false;
				ch.getChangeItemBeans().each {ChangeItemBean item ->
					if(item.getField().toLowerCase() in ["sprint", "status", "story points", "fix version"]) {
						//log?.warn("KEY = '${issue.key}' FIELD='${item.getField()}':::: ${item.fromString}' --> '${item.toString}' ${item.created} (${item.created.getTime()})");
						isChange = true;
					}
				}
				if(isChange) {
					list.add(ch);
				}
			}
		}
		
		return list;
	}
	
	private def getExactLastSprintFromString(String sprints) {
		String sprint = null;
		String release = "Quasar";
		def sprintTemplate = release + "-Sprint-";
		def i = 14; // the number of sprints
		while(i > 0)
		{
			if(sprints?.contains(sprintTemplate + i))
			{
				sprint = sprintTemplate + i;
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
				if(sprints?.contains(sprintTemplate + i))
				{
					sprint = sprintTemplate + i;
					break;
				}
				i--;
			}
		}
		
		return sprint;
	}
	
	private def getExactLastSprint(Issue issue)
	{
		if(!(issue.issueType.name in ["Story", "Spike", "Tech Debt", "DevOps", "Bug"]))
		{
			return null;
		}
		
		def sprints = getCollectionField(issue, "Sprint");
	
		return getExactLastSprintFromString(sprints);
	}
	
	private String getCollectionField(Issue issue, String name) {
		def field = ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName(name);
		def fieldValue = issue.getCustomFieldValue(field);
		return fieldValue?.collect { it.name }?.join(',');
	}
}

public class BurndownChartModelItem {
	private Date _date;
	private Double _actual;
	private Double _ideal;
	
	private String _log;
	
	public BurndownChartModelItem(Date date, Double actual, Double ideal)
	{
		_date = date;
		_actual = actual;
		_ideal = ideal;
	}
	
	public Date getDate() { return _date; }
	public void setDate(Date date) { _date = date; }
	
	public Double getActual() { return _actual; }
	public Double getIdeal() { return _ideal; }
	
	public String getLog() { return _log; }
	public void setLog(String log) { _log = log; }
}

public class LiteIssue {
	private String _key;
	private String _sprint;
	private String _release;
	private String _status;
	private Double _storyPoints;
	
	public LiteIssue(String key, String sprint, String release, String status, Double storyPoints)
	{
		_key = key;
		_sprint = sprint;
		_release = release;
		_status = status;
		_storyPoints = storyPoints;
	}
	
	public String getKey() { return _key; }
	public void setKey(String key) { _key = key; }
	
	public String getSprint() { return _sprint; }
	public void setSprint(String sprint) { _sprint = sprint; }
	
	public String getRelease() { return _release; }
	public void setRelease(String release) { _release = release; }
	
	public String getStatus() { return _status; }
	public void setStatus(String status) { _status = status; }
	
	public Double getStoryPoints() { return _storyPoints; }
	public void setStoryPoints(Double storyPoints) { _storyPoints= storyPoints; }
}

public class Logger {

	private List<String> _log;
	
	public Logger() {
		_log = new ArrayList<String>();
	}
	
	public List<String> getLog() { return _log; }
	
	public void warn(Object obj) {
		_log.add(obj.toString());
	}

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

