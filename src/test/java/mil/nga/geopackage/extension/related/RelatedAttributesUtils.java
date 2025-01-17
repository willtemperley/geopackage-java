package mil.nga.geopackage.extension.related;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.attributes.AttributesDao;
import mil.nga.geopackage.attributes.AttributesResultSet;
import mil.nga.geopackage.attributes.AttributesRow;
import mil.nga.geopackage.attributes.AttributesTable;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.contents.ContentsDataType;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.user.custom.UserCustomColumn;
import mil.nga.geopackage.user.custom.UserCustomResultSet;

public class RelatedAttributesUtils {

	/**
	 * Test related attributes tables
	 * 
	 * @param geoPackage
	 * @throws SQLException
	 */
	public static void testAttributes(GeoPackage geoPackage) throws Exception {

		// Create a related tables extension
		RelatedTablesExtension rte = new RelatedTablesExtension(geoPackage);

		if (rte.has()) {
			rte.removeExtension();
		}

		TestCase.assertFalse(rte.has());
		TestCase.assertTrue(rte.getRelationships().isEmpty());

		// Choose a random attributes table
		List<String> attributesTables = geoPackage.getAttributesTables();
		if (attributesTables.isEmpty()) {
			return; // pass with no testing
		}
		final String baseTableName = attributesTables
				.get((int) (Math.random() * attributesTables.size()));
		final String relatedTableName = attributesTables
				.get((int) (Math.random() * attributesTables.size()));

		// Create and validate a mapping table
		List<UserCustomColumn> additionalMappingColumns = RelatedTablesUtils
				.createAdditionalUserColumns();
		final String mappingTableName = "attributes_attributes";
		UserMappingTable userMappingTable = UserMappingTable
				.create(mappingTableName, additionalMappingColumns);
		TestCase.assertFalse(rte.has(userMappingTable.getTableName()));
		TestCase.assertEquals(
				UserMappingTable.numRequiredColumns()
						+ additionalMappingColumns.size(),
				userMappingTable.getColumns().size());
		UserCustomColumn baseIdColumn = userMappingTable.getBaseIdColumn();
		TestCase.assertNotNull(baseIdColumn);
		TestCase.assertTrue(
				baseIdColumn.isNamed(UserMappingTable.COLUMN_BASE_ID));
		TestCase.assertEquals(GeoPackageDataType.INTEGER,
				baseIdColumn.getDataType());
		TestCase.assertTrue(baseIdColumn.isNotNull());
		TestCase.assertFalse(baseIdColumn.isPrimaryKey());
		UserCustomColumn relatedIdColumn = userMappingTable
				.getRelatedIdColumn();
		TestCase.assertNotNull(relatedIdColumn);
		TestCase.assertTrue(
				relatedIdColumn.isNamed(UserMappingTable.COLUMN_RELATED_ID));
		TestCase.assertEquals(GeoPackageDataType.INTEGER,
				relatedIdColumn.getDataType());
		TestCase.assertTrue(relatedIdColumn.isNotNull());
		TestCase.assertFalse(relatedIdColumn.isPrimaryKey());
		TestCase.assertFalse(rte.has(userMappingTable.getTableName()));

		// Create the relationship between the attributes table and attributes
		// table
		ExtendedRelation extendedRelation = rte.addAttributesRelationship(
				baseTableName, relatedTableName, userMappingTable);
		TestCase.assertTrue(rte.has());
		TestCase.assertTrue(rte.has(userMappingTable.getTableName()));
		TestCase.assertNotNull(extendedRelation);
		List<ExtendedRelation> extendedRelations = rte.getRelationships();
		TestCase.assertEquals(1, extendedRelations.size());
		TestCase.assertTrue(geoPackage.isTable(mappingTableName));

		// Build the Attributes ids
		AttributesDao attributesDao = geoPackage
				.getAttributesDao(baseTableName);
		AttributesResultSet attributesResultSet = attributesDao.queryForAll();
		int attributesCount = attributesResultSet.getCount();
		List<Long> attributeIds = new ArrayList<>();
		while (attributesResultSet.moveToNext()) {
			attributeIds.add(attributesResultSet.getRow().getId());
		}
		attributesResultSet.close();

		// Build the Attribute related ids
		AttributesDao attributesDao2 = geoPackage
				.getAttributesDao(relatedTableName);
		AttributesResultSet attributesResultSet2 = attributesDao2.queryForAll();
		int attributesCount2 = attributesResultSet2.getCount();
		List<Long> attributeIds2 = new ArrayList<>();
		while (attributesResultSet2.moveToNext()) {
			attributeIds2.add(attributesResultSet2.getRow().getId());
		}
		attributesResultSet2.close();

		// Insert user mapping rows between attribute ids and attribute ids
		UserMappingDao dao = rte.getMappingDao(mappingTableName);
		UserMappingRow userMappingRow = null;
		for (int i = 0; i < 10; i++) {
			userMappingRow = dao.newRow();
			userMappingRow.setBaseId(
					attributeIds.get((int) (Math.random() * attributesCount)));
			userMappingRow.setRelatedId(attributeIds2
					.get((int) (Math.random() * attributesCount2)));
			RelatedTablesUtils.populateUserRow(userMappingTable, userMappingRow,
					UserMappingTable.requiredColumns());
			TestCase.assertTrue(dao.create(userMappingRow) > 0);
		}
		TestCase.assertEquals(10, dao.count());

		// Validate the user mapping rows
		userMappingTable = dao.getTable();
		String[] mappingColumns = userMappingTable.getColumnNames();
		UserCustomResultSet resultSet = dao.queryForAll();
		int count = resultSet.getCount();
		TestCase.assertEquals(10, count);
		int manualCount = 0;
		while (resultSet.moveToNext()) {

			UserMappingRow resultRow = dao.getRow(resultSet);
			TestCase.assertFalse(resultRow.hasId());
			TestCase.assertTrue(attributeIds.contains(resultRow.getBaseId()));
			TestCase.assertTrue(
					attributeIds2.contains(resultRow.getRelatedId()));
			RelatedTablesUtils.validateUserRow(mappingColumns, resultRow);
			RelatedTablesUtils.validateDublinCoreColumns(resultRow);

			manualCount++;
		}
		TestCase.assertEquals(count, manualCount);
		resultSet.close();

		ExtendedRelationsDao extendedRelationsDao = rte
				.getExtendedRelationsDao();

		// Get the relations starting from the attributes table
		List<ExtendedRelation> attributesExtendedRelations = extendedRelationsDao
				.getBaseTableRelations(attributesDao.getTableName());
		List<ExtendedRelation> attributesExtendedRelations2 = extendedRelationsDao
				.getTableRelations(attributesDao.getTableName());
		TestCase.assertEquals(1, attributesExtendedRelations.size());
		TestCase.assertEquals(1, attributesExtendedRelations2.size());
		TestCase.assertEquals(attributesExtendedRelations.get(0).getId(),
				attributesExtendedRelations2.get(0).getId());

		// Test the attributes table relations
		for (ExtendedRelation attributesRelation : attributesExtendedRelations) {

			// Test the relation
			TestCase.assertTrue(attributesRelation.getId() >= 0);
			TestCase.assertEquals(attributesDao.getTableName(),
					attributesRelation.getBaseTableName());
			TestCase.assertEquals(attributesDao.getPkColumnName(),
					attributesRelation.getBasePrimaryColumn());
			TestCase.assertEquals(attributesDao2.getTableName(),
					attributesRelation.getRelatedTableName());
			TestCase.assertEquals(attributesDao2.getPkColumnName(),
					attributesRelation.getRelatedPrimaryColumn());
			TestCase.assertEquals(RelationType.ATTRIBUTES.getName(),
					attributesRelation.getRelationName());
			TestCase.assertEquals(mappingTableName,
					attributesRelation.getMappingTableName());

			// Test the user mappings from the relation
			UserMappingDao userMappingDao = rte
					.getMappingDao(attributesRelation);
			UserCustomResultSet mappingResultSet = userMappingDao.queryForAll();
			while (mappingResultSet.moveToNext()) {
				userMappingRow = userMappingDao.getRow(mappingResultSet);
				TestCase.assertTrue(
						attributeIds.contains(userMappingRow.getBaseId()));
				TestCase.assertTrue(
						attributeIds2.contains(userMappingRow.getRelatedId()));
				RelatedTablesUtils.validateUserRow(mappingColumns,
						userMappingRow);
				RelatedTablesUtils.validateDublinCoreColumns(userMappingRow);
			}
			mappingResultSet.close();

		}

		// Get the relations starting from the attributes table
		List<ExtendedRelation> relatedExtendedRelations = extendedRelationsDao
				.getRelatedTableRelations(relatedTableName);
		List<ExtendedRelation> extendedRelations2 = extendedRelationsDao
				.getTableRelations(relatedTableName);
		TestCase.assertEquals(1, relatedExtendedRelations.size());
		TestCase.assertEquals(1, extendedRelations2.size());
		TestCase.assertEquals(relatedExtendedRelations.get(0).getId(),
				extendedRelations2.get(0).getId());

		// Test the attributes table relations
		for (ExtendedRelation relation : relatedExtendedRelations) {

			// Test the relation
			TestCase.assertTrue(relation.getId() >= 0);
			TestCase.assertEquals(attributesDao.getTableName(),
					relation.getBaseTableName());
			TestCase.assertEquals(attributesDao.getPkColumnName(),
					relation.getBasePrimaryColumn());
			TestCase.assertEquals(attributesDao2.getTableName(),
					relation.getRelatedTableName());
			TestCase.assertEquals(attributesDao2.getPkColumnName(),
					relation.getRelatedPrimaryColumn());
			TestCase.assertEquals(RelationType.ATTRIBUTES.getName(),
					relation.getRelationName());
			TestCase.assertEquals(mappingTableName,
					relation.getMappingTableName());

			// Test the user mappings from the relation
			UserMappingDao userMappingDao = rte.getMappingDao(relation);
			int totalMappedCount = userMappingDao.count();
			UserCustomResultSet mappingResultSet = userMappingDao.queryForAll();
			while (mappingResultSet.moveToNext()) {
				userMappingRow = userMappingDao.getRow(mappingResultSet);
				TestCase.assertTrue(
						attributeIds.contains(userMappingRow.getBaseId()));
				TestCase.assertTrue(
						attributeIds2.contains(userMappingRow.getRelatedId()));
				RelatedTablesUtils.validateUserRow(mappingColumns,
						userMappingRow);
				RelatedTablesUtils.validateDublinCoreColumns(userMappingRow);
			}
			mappingResultSet.close();

			// Get and test the attributes DAO
			attributesDao = geoPackage
					.getAttributesDao(attributesDao.getTableName());
			TestCase.assertNotNull(attributesDao);
			AttributesTable attributesTable = attributesDao.getTable();
			TestCase.assertNotNull(attributesTable);
			Contents attributesContents = attributesTable.getContents();
			TestCase.assertNotNull(attributesContents);
			TestCase.assertEquals(ContentsDataType.ATTRIBUTES,
					attributesContents.getDataType());
			TestCase.assertEquals(ContentsDataType.ATTRIBUTES.getName(),
					attributesContents.getDataTypeName());
			TestCase.assertEquals(attributesTable.getTableName(),
					attributesContents.getTableName());
			TestCase.assertNotNull(attributesContents.getLastChange());

			// Get and test the Attributes Rows mapped to each Attributes Row
			attributesResultSet2 = attributesDao2.queryForAll();
			int totalMapped = 0;
			while (attributesResultSet2.moveToNext()) {
				AttributesRow attributes2Row = attributesResultSet2.getRow();
				List<Long> mappedIds = rte.getMappingsForRelated(relation,
						attributes2Row.getId());
				for (long mappedId : mappedIds) {
					AttributesRow attributesRow = attributesDao
							.queryForIdRow(mappedId);
					TestCase.assertNotNull(attributesRow);

					TestCase.assertTrue(attributesRow.hasId());
					TestCase.assertTrue(attributesRow.getId() >= 0);
					TestCase.assertTrue(
							attributeIds.contains(attributesRow.getId()));
					TestCase.assertTrue(
							mappedIds.contains(attributesRow.getId()));
				}

				totalMapped += mappedIds.size();
			}
			attributesResultSet2.close();
			TestCase.assertEquals(totalMappedCount, totalMapped);
		}

		// Delete a single mapping
		int countOfIds = dao.countByIds(userMappingRow);
		TestCase.assertEquals(countOfIds, dao.deleteByIds(userMappingRow));
		TestCase.assertEquals(10 - countOfIds, dao.count());

		// Delete the relationship and user mapping table
		rte.removeRelationship(extendedRelation);
		TestCase.assertFalse(rte.has(userMappingTable.getTableName()));
		extendedRelations = rte.getRelationships();
		TestCase.assertEquals(0, extendedRelations.size());
		TestCase.assertFalse(geoPackage.isTable(mappingTableName));

		// Delete the related tables extension
		rte.removeExtension();
		TestCase.assertFalse(rte.has());

	}

}
