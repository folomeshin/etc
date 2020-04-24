filename = "Wavelength Dashboard - ${new Date().format("yyyy-MM-dd-HH-mm-ss-z", TimeZone.getTimeZone('EST'))}.xlsx".toString();
issues = bpHelper.searchIssues('project = Storyteller AND issuetype in (Epic, Story, Spike, "Tech Debt", DevOps, Bug) AND (fixVersion in (Wavelength, X-Ray) OR (fixVersion = Venus AND (Sprint in (146, 147, 148, 149, 150, 151, 152) OR Sprint is EMPTY ))) AND status not in ("Epic: Cancelled", "Story: Cancelled", "Tech Debt: Cancelled", "DevOps: Cancelled")');