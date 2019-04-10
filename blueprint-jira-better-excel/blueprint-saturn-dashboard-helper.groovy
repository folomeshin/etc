filename = "Saturn Dashboard - ${new Date().format("yyyy-MM-dd-HH-mm-ss-z", TimeZone.getTimeZone('EST'))}.xlsx".toString();
issues = bpHelper.searchIssues('project = Storyteller AND issuetype in (Epic, Story, Spike, "Tech Debt", DevOps, Bug) AND (fixVersion in (Saturn, Titan) OR (fixVersion = Rocket AND (Sprint in (116, 117, 118, 121, 122, 123) OR Sprint is EMPTY ))) AND status not in ("Epic: Cancelled", "Story: Cancelled", "Tech Debt: Cancelled", "DevOps: Cancelled")');