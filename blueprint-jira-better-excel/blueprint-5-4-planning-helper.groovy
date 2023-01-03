filename = "5.4 Release Planning - ${new Date().format("yyyy-MM-dd-HH-mm-ss-z", TimeZone.getTimeZone('EST'))}.xlsx".toString();
issues = bpHelper.searchIssues('project = Storyteller AND issuetype in (Epic, Story, Spike, "Tech Debt", DevOps, Bug) AND fixVersion in ("AgileRM 13.3", "Blueprint 5.4", "Blueprint 5.5") AND status not in ("Epic: Cancelled", "Story: Cancelled", "Tech Debt: Cancelled", "DevOps: Cancelled")');