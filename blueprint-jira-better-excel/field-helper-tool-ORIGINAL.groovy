import com.atlassian.jira.bc.JiraServiceContextImpl
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.AbstractProgressBarSystemField
import com.atlassian.jira.issue.fields.layout.column.ColumnLayoutItemImpl
import com.atlassian.jira.issue.views.util.IssueViewUtil
import com.atlassian.jira.issue.worklog.WorkRatio
import com.atlassian.jira.timezone.TimeZoneService
import org.apache.log4j.Logger


fieldHelper = new FieldHelperTool(user, requestContext.canonicalBaseUrl)

/**
 * Tool to convert JIRA system- and custom fields to their best Excel representations.
 */
public class FieldHelperTool {
	def log = Logger.getLogger(this.getClass())

	/* Configuration. */
	def issueKeyAsLink = true
	def summaryAsLink = true

	/* Insight configuration. */
	def insightObjectsWithNames = true
	def insightObjectsWithKeys = true
	def insightObjectsWithAttributes = false

	def commentManager = ComponentAccessor.commentManager
	def customFieldManager = ComponentAccessor.customFieldManager
	def issueViewUtil = ComponentAccessor.getOSGiComponentInstanceOfType(IssueViewUtil.class)
	def timeZoneService = ComponentAccessor.getOSGiComponentInstanceOfType(TimeZoneService.class)

	def workRatio = new WorkRatio()
	def xmlSlurper = new XmlSlurper()

	/* Insight dependencies. */
	def insightObjectFacade
	def insightObjectTypeAttributeFacade

	/* nFeed dependencies. */
	def nFeedInitialized = false
	def nFeedFieldDisplayService

	def user
	def baseUrl
	def calendar

	private static EXCEL_PARENT_KEY_FIELD_NAME = "Excel parent key"
	private static EXCEL_COMMENTS_CALCULATED_FIELD_NAME = "Excel comments"
	private static EXCEL_FIRST_COMMENT_CALCULATED_FIELD_NAME = "Excel first comment"
	private static EXCEL_FIRST_COMMENT_TEXT_CALCULATED_FIELD_NAME = "Excel first comment text"
	private static EXCEL_FIRST_COMMENT_AUTHOR_CALCULATED_FIELD_NAME = "Excel first comment user"
	private static EXCEL_FIRST_COMMENT_DATE_CALCULATED_FIELD_NAME = "Excel first comment date"
	private static EXCEL_LAST_COMMENT_CALCULATED_FIELD_NAME = "Excel last comment"
	private static EXCEL_LAST_COMMENT_TEXT_CALCULATED_FIELD_NAME = "Excel last comment text"
	private static EXCEL_LAST_COMMENT_AUTHOR_CALCULATED_FIELD_NAME = "Excel last comment user"
	private static EXCEL_LAST_COMMENT_DATE_CALCULATED_FIELD_NAME = "Excel last comment date"

	private static CALCULATED_FIELD_NAMES = [
			EXCEL_PARENT_KEY_FIELD_NAME,
			EXCEL_COMMENTS_CALCULATED_FIELD_NAME,
			EXCEL_FIRST_COMMENT_CALCULATED_FIELD_NAME, EXCEL_FIRST_COMMENT_TEXT_CALCULATED_FIELD_NAME, EXCEL_FIRST_COMMENT_AUTHOR_CALCULATED_FIELD_NAME, EXCEL_FIRST_COMMENT_DATE_CALCULATED_FIELD_NAME,
			EXCEL_LAST_COMMENT_CALCULATED_FIELD_NAME, EXCEL_LAST_COMMENT_TEXT_CALCULATED_FIELD_NAME, EXCEL_LAST_COMMENT_AUTHOR_CALCULATED_FIELD_NAME, EXCEL_LAST_COMMENT_DATE_CALCULATED_FIELD_NAME
	]

	public FieldHelperTool(def user, def baseUrl) {
		this.user = user
		this.baseUrl = baseUrl

		// set up XML slurper
		xmlSlurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		xmlSlurper.setFeature("http://xml.org/sax/features/namespaces", false)
		xmlSlurper.setFeature("http://xml.org/sax/features/external-general-entities", false)

		// create calendar with the user's time zone
		def jiraServiceContext = new JiraServiceContextImpl(user)
		def timeZone = timeZoneService.getUserTimeZoneInfo(jiraServiceContext).toTimeZone()
		calendar = new GregorianCalendar(timeZone)
	}

	/**
	 * Returns whether the passed field ID is related to a custom field (true) or a system field (false).
	 */
	private static boolean isCustomField(String fieldId) {
		fieldId.startsWith("customfield_")
	}

	private static boolean isCustomField(field) {
		isCustomField(field.id)
	}

	/**
	 * Returns whether the passed field name is related to a calculated field.
	 */
	private static boolean isCalculatedField(String customFieldName) {
		customFieldName in CALCULATED_FIELD_NAMES
	}

	private static boolean isCalculatedField(customField) {
		isCalculatedField(customField.name)
	}

	/**
	 * Returns the system-, custom- or calculated field value for the issue.
	 * Note: the returned value is strongly typed (String, Date, etc.).
	 *
	 * @param fieldId is either the ID of a system field (e.g. "assignee"),
	 * 				ID of a custom field (e.g. "customfield_10123"),
	 * 				or the ID of a calculated field (e.g. "Excel parent key").
	 * @param columnLayoutItem (optional) should be passed when the value is
	 *				to be obtained in an Issue Navigator context.
	 */
	public def getFieldValue(issue, String fieldId, columnLayoutItem = null) {
		if(isCalculatedField(fieldId)) {
			return getCalculatedFieldValue(issue, fieldId)
		}
		if(!isCustomField(fieldId)) {
			return getSystemFieldValue(issue, fieldId)
		}

		def customField = customFieldManager.getCustomFieldObject(fieldId)
		if(customField == null) {
			return "${fieldId} not found"
		}

		if(isCalculatedField(customField)) {
			return getCalculatedFieldValue(issue, customField)
		}

		return getCustomFieldValue(issue, customField, columnLayoutItem)
	}

	public def getFieldValue(issue, field, columnLayoutItem = null) {
		getFieldValue(issue, field.id, columnLayoutItem)
	}

	/**
	 * This convenience method is only to be used if you want to get a field value by the field name.
	 * The #getFieldValue(issue, String fieldId, columnLayoutItem = null) method is generally a safer option, because that accepts the immutable field ID (e.g. "customfield_10123"), not the name.
	 * This is more convenient because it can work out of the box without configuring the custom field IDs in the templates, but breaks when the custom field is renamed.
	 */
	public def getFieldValueByName(issue, String fieldName, columnLayoutItem = null) {
		def customField = customFieldManager.getCustomFieldObjectByName(fieldName)
		if(customField == null) {
			return "\"${fieldName}\" not found"
		}

		return getCustomFieldValue(issue, customField, columnLayoutItem)
	}

	/**
	 * This convenience method is similar to the previous one, but tolerates custom field renamings by receiving multiple name variants.
	 *
	 * @param fieldNameVariants is an array with multiple custom field names.
	 * 				The value of the first existing custom field name will be returned.
	 */
	public def getFieldValueByName(issue, String[] fieldNameVariants, columnLayoutItem = null) {
		def customField = null

		for(fieldName in fieldNameVariants) {
			customField = customFieldManager.getCustomFieldObjectByName(fieldName)
			if(customField) {
				break
			}
		}
		if(customField == null) {
			return "${fieldNameVariants} not found"
		}

		return getCustomFieldValue(issue, customField, columnLayoutItem)
	}


	private def getSystemFieldValue(issue, fieldId) {
		switch(fieldId) {
			case "project":
				issue.projectObject.name
				break

			case "versions":
				issue.affectedVersions.collect{ it.name }.join(", ")
				break

			case "assignee":
				nullSafeUser(issue.assignee)
				break

			case "components":
				issue.components.collect{ it.name }.join(", ")
				break

			case "comment":
				"${fieldId} not supported yet"
				break

			case "description":
				issue.description
				break

			case "duedate":
				nullSafeDate(issue.dueDate)
				break

			case "environment":
				issue.environment
				break

			case "fixVersions":
				issue.fixVersions.collect{ it.name }.join(", ")
				break

			case "issuekey":
				issueKeyAsLink ? "${issue.key}|${baseUrl}/browse/${issue.key}" : issue.key
				break

			case "number":
				"${fieldId} not supported yet"
				break

			case "issuetype":
				issue.issueTypeObject.nameTranslation
				break

			case "thumbnail":
				"${fieldId} not supported yet"
				break

			case "issuelinks":
				def linkCollection = issueViewUtil.getLinkCollection(issue, user)
				linkCollection.allIssues.collect { it.key }.join(", ")
				break

			case "lastViewed":
				"${fieldId} not supported yet"
				break

			case "workratio":
				def percentage = workRatio.getWorkRatio(issue)
				(percentage >= 0) ? (percentage / 100.0) : ""
				break

			case "subtasks":
				issue.subTaskObjects.collect{ it.key }.join(", ")
				break

			case "attachment":
				"${fieldId} not supported yet"
				break

			case "priority":
				issue.priorityObject.nameTranslation
				break

			case "reporter":
				nullSafeUser(issue.reporter)
				break

			case "creator":
				nullSafeUser(issue.creator)
				break

			case "security":
				issue.securityLevel.name
				break

			case "summary":
				summaryAsLink ? "${issue.summary}|${baseUrl}/browse/${issue.key}" : issue.summary
				break

			case "timetracking":
				"${fieldId} not supported yet"
				break

			case "created":
				nullSafeDateTime(issue.getCreated())
				break

			case "updated":
				nullSafeDateTime(issue.updated)
				break

			case "resolutiondate":
				nullSafeDateTime(issue.resolutionDate)
				break

			case "status":
				issue.statusObject.nameTranslation
				break

			case "resolution":
				issue.resolutionObject.name
				break

			case "labels":
				issue.labels.join(", ")
				break

			case "worklog":
				"${fieldId} not supported yet"
				break

			case "timeoriginalestimate":
				secondsToHours(issue.originalEstimate)
				break

			case "timeestimate":
				secondsToHours(issue.estimate)
				break

			case "timespent":
				secondsToHours(issue.timeSpent)
				break

			case "aggregatetimespent":
				secondsToHours(issue.aggregateTimeSpent)
				break

			case "aggregatetimeestimate":
				secondsToHours(issue.aggregateRemainingEstimate)
				break

			case "aggregatetimeoriginalestimate":
				secondsToHours(issue.aggregateOriginalEstimate)
				break

			case "aggregateprogress":
				def percentage = AbstractProgressBarSystemField.calculateProgressPercentage(issue.aggregateTimeSpent, issue.aggregateRemainingEstimate)
				(percentage >= 0) ? (percentage / 100.0) : ""
				break

			case "progress":
				def percentage = AbstractProgressBarSystemField.calculateProgressPercentage(issue.timeSpent, issue.estimate)
				(percentage >= 0) ? (percentage / 100.0) : ""
				break

			case "votes":
				issue.votes
				break

			case "voter":
				"${fieldId} not supported yet"
				break

			case "watches":
				issue.watches
				break

			case "watcher":
				"${fieldId} not supported yet"
				break

			default:
				"${fieldId} not supported yet"
				break
		}
	}

	private def getCustomFieldValue(issue, customField, columnLayoutItem) {
		def customFieldTypeKey = customField.customFieldType.key
		def value = issue.getCustomFieldValue(customField)

		switch(customFieldTypeKey) {
			case "com.atlassian.jira.plugin.system.customfieldtypes:multicheckboxes":
			case "com.atlassian.jira.plugin.system.customfieldtypes:multiselect":
				(value != null) ? value.join(", ") : null
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:datepicker":
				nullSafeDate(value)
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:datetime":
				nullSafeDateTime(value)
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:multigrouppicker":
			case "com.atlassian.jira.plugin.system.customfieldtypes:grouppicker":
			case "com.atlassian.jira.plugin.system.customfieldtypes:multiversion":
			case "com.atlassian.jira.plugin.system.customfieldtypes:version":
				value.collect{ it.name }.join(", ")
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:labels":
				(value != null) ? value.collect{ it.label }.join(", ") : null
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:float":
				value // to keep its original Double type (not to convert it to String)
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:project":
				value.name
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:radiobuttons":
				value.value
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:cascadingselect":
				(value != null) ? value.get(null).toString() + ((value.get("1") != null) ? " - " + value.get("1").toString() : "") : null
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:select":
				value.value
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:textarea":
				value
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:textfield":
				value
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:url":
				value
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:multiuserpicker":
				(value != null) ? value.collect{ nullSafeUser(it) }.join(", ") : null
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:userpicker":
				nullSafeUser(value)
				break

			// JIRA Agile custom field types
			case "com.pyxis.greenhopper.jira:gh-sprint":
				(value != null) ? value.collect{ it.name }.join(", ") : null
				break

			case "com.pyxis.greenhopper.jira:gh-epic-color":
				value
				break

			case "com.pyxis.greenhopper.jira:gh-epic-link":
				customField.customFieldType.getEpicDisplayName(value)
				break

			case "com.pyxis.greenhopper.jira:gh-epic-label":
				value
				break

			case "com.pyxis.greenhopper.jira:gh-epic-status":
				value
				break

			case "com.pyxis.greenhopper.jira:gh-lexo-rank":
				"${customFieldTypeKey} not supported yet"
				break

			// JIRA Service Desk custom field types
			case "com.atlassian.servicedesk:sd-request-participants":
				(value != null) ? value.collect{ nullSafeUser(it) }.join(", ") : null
				break

			case "com.atlassian.servicedesk:sd-sla-field":
				if(value != null) {
					def text = getFieldValueAsText(issue, customField, columnLayoutItem)
					if(!text.isAllWhitespace()) {
						def parts = text.split(':')
						if(parts.size() == 2) {
							def sign = (parts[0].indexOf('-') == -1) ? 1 : -1
							def hours = parts[0].replace("-","").toInteger()
							def minutes = parts[1].toInteger()
							sign*(hours + minutes/60.0) // hours as decimals number (-1:45 is exported as "-1.75" and formatted as red "-1.75 h")
						}
					}
				} else {
					return null
				}
				break

			case "com.atlassian.servicedesk:vp-origin":
				customField.customFieldType.getDisplayedValue(value).name() // not a Java-style getter as it is in Scala
				break

			// Insight custom field types
			case "com.riadalabs.jira.plugins.insight:rlabs-customfield-object":
			case "com.riadalabs.jira.plugins.insight:rlabs-customfield-object-reference":
			case "com.riadalabs.jira.plugins.insight:rlabs-customfield-object-readonly":
			case "com.riadalabs.jira.plugins.insight:rlabs-customfield-object-multi":
			case "com.riadalabs.jira.plugins.insight:rlabs-customfield-object-reference-multi":
				(value != null) ? value.collect{ insightObjectToString(it) }.sort{ a,b -> a.toLowerCase() <=> b.toLowerCase() }.join(", ") : null
				break

			// nFeed custom field types
			case "com.valiantys.jira.plugins.SQLFeed:nfeed-standard-customfield-type":
			case "com.valiantys.jira.plugins.SQLFeed:com.valiantys.jira.plugins.sqlfeed.user.customfield.type":
			case "com.valiantys.jira.plugins.SQLFeed:com.valiantys.jira.plugins.sqlfeed.customfield.type": // deprecated field type
				(value != null) ? xmlToDelimitedString(nFeedDisplayToString(issue, customField), ", ") : null
				break

			case "com.valiantys.jira.plugins.SQLFeed:nfeed-date-customfield-type":
				nullSafeDate(value)
				break

			case "com.valiantys.jira.plugins.SQLFeed:nfeed-datetime-customfield-type":
				nullSafeDateTime(value)
				break

			// Tempo custom field types
			case "com.tempoplugin.tempo-accounts:accounts.customfield":
				customField.customFieldType.getChangelogString(customField, value)
				break

			case "com.tempoplugin.tempo-plan-core:tp.iteration.customfield":
				// due to a Tempo bug, the rendered HTML value is broken (no quotes around the value of a href attribute) and we cannot parse it
				// we must fall back to the value object's toString() method, thus the rendered output will be a numeric ID value
				(value != null) ? getFieldValueAsText(value) : null
				break

			case "com.tempoplugin.tempo-teams:team.customfield":
				(value != null) ? getFieldValueAsText(issue, customField, columnLayoutItem) : null
				break

			default:
				(columnLayoutItem != null) ? getFieldValueAsText(issue, customField, columnLayoutItem) : getFieldValueAsText(value)
				break
		}
	}

	private def getCalculatedFieldValue(issue, customField) {
		return getCalculatedFieldValue(issue, customField.name)
	}

	private def getCalculatedFieldValue(issue, String fieldName) {
		def comments = commentManager.getCommentsForUser(issue, user)

		switch(fieldName) {
			case EXCEL_PARENT_KEY_FIELD_NAME:
				issue.isSubTask() ? (issueKeyAsLink ? "${issue.parentObject.key}|${baseUrl}/browse/${issue.parentObject.key}" : issue.parentObject.key) : ""
				break

			case EXCEL_COMMENTS_CALCULATED_FIELD_NAME:
				!comments.empty ? comments.collect{ commentToString(it) }.join("\n\n") : ""
				break

			case EXCEL_FIRST_COMMENT_CALCULATED_FIELD_NAME:
				!comments.empty ? commentToString(comments.first()) : ""
				break

			case EXCEL_FIRST_COMMENT_TEXT_CALCULATED_FIELD_NAME:
				!comments.empty ? comments.first().body : ""
				break

			case EXCEL_FIRST_COMMENT_AUTHOR_CALCULATED_FIELD_NAME:
				!comments.empty ? comments.first().authorFullName : ""
				break

			case EXCEL_FIRST_COMMENT_DATE_CALCULATED_FIELD_NAME:
				!comments.empty ? nullSafeDateTime(comments.first().created) : ""
				break

			case EXCEL_LAST_COMMENT_CALCULATED_FIELD_NAME:
				!comments.empty ? commentToString(comments.last()) : ""
				break

			case EXCEL_LAST_COMMENT_TEXT_CALCULATED_FIELD_NAME:
				!comments.empty ? comments.last().body : ""
				break

			case EXCEL_LAST_COMMENT_AUTHOR_CALCULATED_FIELD_NAME:
				!comments.empty ? comments.last().authorFullName : ""
				break

			case EXCEL_LAST_COMMENT_DATE_CALCULATED_FIELD_NAME:
				!comments.empty ? nullSafeDateTime(comments.last().created) : ""
				break

			default:
				"${fieldName} is an undefined calculated field"
				break
		}
	}

	/**
	 * Returns the Excel cell type for the issue field.
	 * It is one of "number", "date" or "string".
	 */
	public def getFieldCellType(String fieldId) {
		if(isCalculatedField(fieldId)) {
			return getCalculatedFieldCellType(issue, fieldId)
		}
		if(!isCustomField(fieldId)) {
			return getSystemFieldCellType(fieldId)
		}

		def customField = customFieldManager.getCustomFieldObject(fieldId)
		if(customField == null)  {
			return "string"
		}

		if(isCalculatedField(customField)) {
			return getCalculatedFieldCellType(customField)
		}

		return getCustomFieldCellType(customField)
	}

	public def getFieldCellType(field) {
		getFieldCellType(field.id)
	}

	private def getSystemFieldCellType(fieldId) {
		switch(fieldId) {
			case "workratio":
			case "timeoriginalestimate":
			case "timeestimate":
			case "timespent":
			case "aggregatetimespent":
			case "aggregatetimeestimate":
			case "aggregatetimeoriginalestimate":
			case "aggregateprogress":
			case "progress":
			case "votes":
			case "watches":
				"number"
				break

			case "duedate":
			case "created":
			case "updated":
			case "resolutiondate":
				"date"
				break

			case "issuekey":
				issueKeyAsLink ? "link" : "string"
				break

			case "summary":
				summaryAsLink ? "link" : "string"
				break

			default:
				"string"
		}
	}

	private def getCustomFieldCellType(customField) {
		switch(customField.customFieldType.key) {
			case "com.atlassian.jira.plugin.system.customfieldtypes:datepicker":
			case "com.atlassian.jira.plugin.system.customfieldtypes:datetime":
				"date"
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:float":
				"number"
				break

			// JIRA Service Desk custom field types
			case "com.atlassian.servicedesk:sd-sla-field":
				"number"
				break

			// nFeed custom field types
			case "com.valiantys.jira.plugins.SQLFeed:nfeed-date-customfield-type":
			case "com.valiantys.jira.plugins.SQLFeed:nfeed-datetime-customfield-type":
				"date"
				break

			default:
				"string"
		}
	}

	private def getCalculatedFieldCellType(customField) {
		return getCalculatedFieldCellType(customField.name)
	}

	private def getCalculatedFieldCellType(String fieldName) {
		switch(fieldName) {
			case EXCEL_PARENT_KEY_FIELD_NAME:
				issueKeyAsLink ? "link" : "string"
				break

			case EXCEL_FIRST_COMMENT_DATE_CALCULATED_FIELD_NAME:
			case EXCEL_LAST_COMMENT_DATE_CALCULATED_FIELD_NAME:
				"date"
				break

			default:
				"string"
		}
	}

	/**
	 * Returns the format specifier string (ex: "dd/mmm/yyyy") for the issue field.
	 * The format specifier is typically used for number- or date type fields.
	 */
	public def getFieldFormat(String fieldId) {
		if(isCalculatedField(fieldId)) {
			return getCalculatedFieldFormat(issue, fieldId)
		}
		if(!isCustomField(fieldId)) {
			return getSystemFieldFormat(fieldId)
		}

		def customField = customFieldManager.getCustomFieldObject(fieldId)
		if(customField == null) {
			return null
		}

		if(isCalculatedField(customField)) {
			return getCalculatedFieldFormat(customField)
		}

		return getCustomFieldFormat(customField)
	}

	public def getFieldFormat(field) {
		getFieldFormat(field.id)
	}

	private def getSystemFieldFormat(fieldId) {
		switch(fieldId) {
			case "workratio":
			case "progress":
			case "aggregateprogress":
				"0.00%"
				break

			case "timeoriginalestimate":
			case "timeestimate":
			case "timespent":
			case "aggregatetimespent":
			case "aggregatetimeestimate":
			case "aggregatetimeoriginalestimate":
				"0.00"
				break

			case "duedate":
			case "created":
			case "updated":
			case "resolutiondate":
				"dd/mmm/yyyy"
				break

			default:
				// no custom format
				null
		}
	}

	private def getCustomFieldFormat(customField) {
		switch(customField.customFieldType.key) {
			case "com.atlassian.jira.plugin.system.customfieldtypes:datepicker":
				"dd/mmm/yyyy"
				break

			case "com.atlassian.jira.plugin.system.customfieldtypes:datetime":
				"dd/mmm/yyyy hh:mm"
				break

			// JIRA Service Desk custom field types
			case "com.atlassian.servicedesk:sd-sla-field":
				"0.00\" h\";[RED]-0.00\" h\""
				break

			// nFeed custom field types
			case "com.valiantys.jira.plugins.SQLFeed:nfeed-date-customfield-type":
				"dd/mmm/yyyy"
				break

			case "com.valiantys.jira.plugins.SQLFeed:nfeed-datetime-customfield-type":
				"dd/mmm/yyyy hh:mm"
				break

			default:
				// no custom format
				null
		}
	}

	private def getCalculatedFieldFormat(customField) {
		return getCalculatedFieldFormat(customField.name);
	}

	private def getCalculatedFieldFormat(String fieldName) {
		switch(fieldName) {
			case EXCEL_FIRST_COMMENT_DATE_CALCULATED_FIELD_NAME:
			case EXCEL_LAST_COMMENT_DATE_CALCULATED_FIELD_NAME:
				"dd/mmm/yyyy hh:mm"
				break

			default:
				// no custom format
				null
		}
	}

	/**
	 * Returns the raw rendered HTML value for fields without an explicit value formatter.
	 */
	private def getFieldValueAsHtml(issue, columnLayoutItem) {
		columnLayoutItem.getHtml([:], issue).replace("&nbsp;", " ")
	}

	/**
	 * Returns the raw text value for fields without an explicit value formatter.
	 * (This method relies on parsing the text parts from the HTML value.)
	 */
	private def getFieldValueAsText(issue, field, columnLayoutItem) {
		// create a dummy column layout item if not passed (necessary for the HTML rendering)
		if(columnLayoutItem == null) {
			columnLayoutItem = new ColumnLayoutItemImpl(field, 1)
		}

		// render HTML
		def valueHtml = "<span>" + getFieldValueAsHtml(issue, columnLayoutItem) + "</span>"

		// get text value from the HTML value
		def value = null
		if(!valueHtml.isAllWhitespace()) {
			try {
				def textPart = xmlSlurper.parseText(valueHtml)
				value = textPart.text()
			} catch(Throwable ex) {
				valueHtml = ex.message
			}
		}

		return (value != null) ? value.trim() : valueHtml
	}

	/**
	 * Returns the value for textual fields without an explicit value formatter.
	 * This method relies on the toString() method.
	 */
	private def getFieldValueAsText(value) {
		value.toString().trim()
	}

	/**
	 * Returns the string that contains the comment text, the comment author and the comment date.
	 */
	private def commentToString(def comment) {
		def commentString = comment.body + "\n---"

		def commentUser = nullSafeUser(comment.authorApplicationUser)
		if(!"".equals(commentUser)) {
			commentString += " by " + commentUser
		}

		def commentCalendar = nullSafeDateTime(comment.created)
		if(!"".equals(commentCalendar)) {
			commentString += " at " + commentCalendar.format("dd/MMM/yyyy HH:mm")
		}

		return commentString
	}

	/**
	 * Should be for the values without time component and time zone.
	 */
	private def nullSafeDate(def date) {
		(date != null) ? date : ""
	}

	/**
	 * Should be for the values with time component and time zone.
	 */
	private def nullSafeDateTime(def date) {
		if (date != null) {
			calendar.setTime(date)
			return calendar
		} else {
			return ""
		}
	}

	private static def nullSafeUser(def user) {
		(user != null) ? user.displayName : ""
	}

	/**
	 * Returns the value or 0 if the value is <code>null</code>.
	 */
	private static def nullSafeNumber(def number) {
		(number != null) ? number : 0
	}

	private static Double secondsToHours(def seconds) {
		(seconds != null) ? (seconds / (60.0*60.0)) : null
	}

	// - Insight integration --------------------------------------------------

	/**
	 * Returns the string that contains the Insight object's name, key (optionally) and attributes (optionally).
	 */
	private def insightObjectToString(def object) {
		def objectStringBuilder = StringBuilder.newInstance()

		if(insightObjectsWithNames) {
			objectStringBuilder << object.name
		}

		if(insightObjectsWithKeys) {
			if(objectStringBuilder.size() > 0) {
				objectStringBuilder << " "
			}
			objectStringBuilder << "[" << object.objectKey << "]"
		}

		if(insightObjectsWithAttributes) {
			// lazily load dependencies
			if(insightObjectFacade == null) {
				insightObjectFacade = ComponentAccessor.getOSGiComponentInstanceOfType(ComponentAccessor.pluginAccessor.classLoader.findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade"))
			}
			if(insightObjectTypeAttributeFacade == null) {
				insightObjectTypeAttributeFacade = ComponentAccessor.getOSGiComponentInstanceOfType(ComponentAccessor.pluginAccessor.classLoader.findClass("com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade"))
			}

			def attributesString = object.objectAttributeBeans.collect{ objectAttribute ->
				def nameAndValueStringBuilder = StringBuilder.newInstance()
				if(objectAttribute.id != null) {
					def attribute = insightObjectTypeAttributeFacade.loadObjectTypeAttributeBean(objectAttribute.objectTypeAttributeId)
					if(attribute != null) {
						nameAndValueStringBuilder << attribute.name
						nameAndValueStringBuilder << "="
						nameAndValueStringBuilder << objectAttribute.objectAttributeValueBeans.collect{ attributeValue ->
							(attributeValue.referencedObjectBeanId == null) ? attributeValue.value.toString() : insightObjectFacade.loadObjectBean(attributeValue.referencedObjectBeanId)?.name
						}.join(";")
					}
				}
				return nameAndValueStringBuilder.toString()
			}.findAll().join(", ")

			if(objectStringBuilder.size() > 0) {
				objectStringBuilder << " "
			}
			objectStringBuilder << "(" << attributesString << ")"
		}

		return objectStringBuilder.toString()
	}

	// - nFeed integration ----------------------------------------------------

	private def nFeedDisplayToString(def issue, def customField) {
		// lazily load dependency (attempt only once)
		if(!nFeedInitialized) {
			try {
				def fieldDisplayServiceClass = Class.forName("com.valiantys.nfeed.api.IFieldDisplayService")
				nFeedFieldDisplayService = ComponentAccessor.getOSGiComponentInstanceOfType(fieldDisplayServiceClass)
			} catch (Exception ex) {
				log.warn("Failed to find the nFeed API", ex)
			}
			nFeedInitialized = true
		}

		(nFeedFieldDisplayService != null) ? nFeedFieldDisplayService.getDisplayResult(issue.id, customField.id).getDisplay() : getFieldValueAsText(issue.getCustomFieldValue(customField))
	}

	/**
	 * Returns the body text fragments from the passed XML joined with the delimiter.
	 * The XML is not required to be well-formatted and can even be plain text.
	 */
	private def xmlToDelimitedString(def xml, def delimiter) {
		xml.split(/<.*?>/).collect{ it.trim() }.findAll{ !it.allWhitespace }.join(delimiter)
	}
}
