filename = "7.0 Release Planning - ${new Date().format("yyyy-MM-dd-HH-mm-ss-z", TimeZone.getTimeZone('EST'))}.xlsx".toString();
issues = bpHelper.searchIssues('project = Storyteller AND issuetype in (Epic, Story, Spike, "Tech Debt", DevOps, Bug) AND fixVersion in ("AgileRM 15.0", "Blueprint 7.0", "Blueprint 7.1") AND status not in ("Epic: Cancelled", "Story: Cancelled", "Tech Debt: Cancelled", "DevOps: Cancelled")');