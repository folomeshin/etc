// Get Query Result
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.web.bean.PagerFilter;

def jqlSearch = 'project = Storyteller AND assignee = alex.folomechine'; // Create the JQL query to perform

SearchService searchService = ComponentAccessor.getComponent(SearchService.class);
def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();

// Set the default list of issues to null just to be safe 
//List<Issue> issues = null;

// Perform the search as a user and return the result so it can be validated. 
SearchService.ParseResult parseResult = searchService.parseQuery(user, jqlSearch);

if (parseResult.isValid()) {

	def searchResult = searchService.search(user, parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
	issues = searchResult.issues
} 
else { 
	// Log errors if invalid JQL is used so we can fix it
	log.error("Invalid JQL: " + jqlSearch);
}

log.warn(issues.size());