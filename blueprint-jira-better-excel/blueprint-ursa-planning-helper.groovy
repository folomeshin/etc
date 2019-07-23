filename = "Ursa Release Planning - ${new Date().format("yyyy-MM-dd-HH-mm-ss-z", TimeZone.getTimeZone('EST'))}.xlsx".toString();
issues = bpHelper.searchIssues('project = Storyteller AND issuetype in (Epic, Story, Spike, "Tech Debt", DevOps, Bug) AND (fixVersion in (Ursa) OR (fixVersion = Titan AND (Sprint in (132, 133, 134, 135, 136, 137, 138) OR Sprint is EMPTY ))) AND status not in ("Epic: Cancelled", "Story: Cancelled", "Tech Debt: Cancelled", "DevOps: Cancelled")');