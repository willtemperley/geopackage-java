package mil.nga.geopackage.tiles.user;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;

import junit.framework.TestCase;
import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.TestUtils;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.contents.ContentsDao;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.db.ResultUtils;
import mil.nga.geopackage.db.SQLUtils;
import mil.nga.geopackage.db.SQLiteQueryBuilder;
import mil.nga.geopackage.geom.GeoPackageGeometryDataUtils;
import mil.nga.geopackage.tiles.TileBoundingBoxUtils;
import mil.nga.geopackage.tiles.TileGrid;
import mil.nga.geopackage.tiles.matrix.TileMatrix;
import mil.nga.geopackage.tiles.matrix.TileMatrixDao;
import mil.nga.geopackage.tiles.matrix.TileMatrixKey;
import mil.nga.geopackage.tiles.matrixset.TileMatrixSet;
import mil.nga.geopackage.tiles.matrixset.TileMatrixSetDao;
import mil.nga.geopackage.user.ColumnValue;
import mil.nga.proj.ProjectionConstants;
import mil.nga.sf.proj.GeometryTransform;

/**
 * Tiles Utility test methods
 * 
 * @author osbornb
 */
public class TileUtils {

	/**
	 * Test read
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 */
	public static void testRead(GeoPackage geoPackage) throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				// Test the get tile DAO methods
				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				TestCase.assertNotNull(dao);
				dao = geoPackage.getTileDao(tileMatrixSet.getContents());
				TestCase.assertNotNull(dao);
				dao = geoPackage.getTileDao(tileMatrixSet.getTableName());
				TestCase.assertNotNull(dao);

				TestCase.assertNotNull(dao.getDb());
				TestCase.assertEquals(tileMatrixSet.getId(),
						dao.getTileMatrixSet().getId());
				TestCase.assertEquals(tileMatrixSet.getTableName(),
						dao.getTableName());
				TestCase.assertFalse(dao.getTileMatrices().isEmpty());

				TileTable tileTable = dao.getTable();
				String[] columns = tileTable.getColumnNames();
				int zoomLevelIndex = tileTable.getZoomLevelColumnIndex();
				TestCase.assertTrue(
						zoomLevelIndex >= 0 && zoomLevelIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_ZOOM_LEVEL,
						columns[zoomLevelIndex]);
				int tileColumnIndex = tileTable.getTileColumnColumnIndex();
				TestCase.assertTrue(tileColumnIndex >= 0
						&& tileColumnIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_TILE_COLUMN,
						columns[tileColumnIndex]);
				int tileRowIndex = tileTable.getTileRowColumnIndex();
				TestCase.assertTrue(
						tileRowIndex >= 0 && tileRowIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_TILE_ROW,
						columns[tileRowIndex]);
				int tileDataIndex = tileTable.getTileDataColumnIndex();
				TestCase.assertTrue(
						tileDataIndex >= 0 && tileDataIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_TILE_DATA,
						columns[tileDataIndex]);

				// Query for all
				TileResultSet tileResultSet = dao.queryForAll();
				int count = tileResultSet.getCount();
				int manualCount = 0;
				for (TileRow tileRow : tileResultSet) {

					validateTileRow(dao, columns, tileRow);

					manualCount++;
				}
				TestCase.assertEquals(count, manualCount);
				tileResultSet.close();

				// Manually query for all and compare
				Connection connection = dao.getConnection();
				String sql = SQLiteQueryBuilder.buildQueryString(false,
						dao.getTableName(), null, null, null, null, null, null,
						null);
				ResultSet resultSet = SQLUtils.query(connection, sql, null);
				int resultSetCount = SQLUtils.count(connection, sql, null);
				tileResultSet = new TileResultSet(tileTable, resultSet,
						resultSetCount);
				count = tileResultSet.getCount();
				manualCount = 0;
				while (tileResultSet.moveToNext()) {
					manualCount++;
				}
				TestCase.assertEquals(count, manualCount);

				TestCase.assertTrue("No tiles to test", count > 0);

				tileResultSet.close();

				resultSet = SQLUtils.query(connection, sql, null);
				resultSetCount = SQLUtils.count(connection, sql, null);
				tileResultSet = new TileResultSet(tileTable, resultSet,
						resultSetCount);

				// Choose random tile
				int random = (int) (Math.random() * count);
				tileResultSet.moveToPosition(random);
				TileRow tileRow = tileResultSet.getRow();

				tileResultSet.close();

				// Query by id
				TileRow queryTileRow = dao.queryForIdRow(tileRow.getId());
				TestCase.assertNotNull(queryTileRow);
				TestCase.assertEquals(tileRow.getId(), queryTileRow.getId());

				// Find two non id columns
				TileColumn column1 = null;
				TileColumn column2 = null;
				for (TileColumn column : tileRow.getTable().getColumns()) {
					if (!column.isPrimaryKey()) {
						if (column1 == null) {
							column1 = column;
						} else {
							column2 = column;
							break;
						}
					}
				}

				// Query for equal
				if (column1 != null) {

					Object column1Value = tileRow.getValue(column1.getName());
					Class<?> column1ClassType = column1.getDataType()
							.getClassType();
					boolean column1Decimal = column1ClassType == Double.class
							|| column1ClassType == Float.class;
					ColumnValue column1TileValue;
					if (column1Decimal) {
						column1TileValue = new ColumnValue(column1Value,
								.000001);
					} else {
						column1TileValue = new ColumnValue(column1Value);
					}
					tileResultSet = dao.queryForEq(column1.getName(),
							column1TileValue);
					TestCase.assertTrue(tileResultSet.getCount() > 0);
					boolean found = false;
					while (tileResultSet.moveToNext()) {
						queryTileRow = tileResultSet.getRow();
						TestCase.assertEquals(column1Value,
								queryTileRow.getValue(column1.getName()));
						if (!found) {
							found = tileRow.getId() == queryTileRow.getId();
						}
					}
					TestCase.assertTrue(found);
					tileResultSet.close();

					// Query for field values
					Map<String, ColumnValue> fieldValues = new HashMap<String, ColumnValue>();
					fieldValues.put(column1.getName(), column1TileValue);
					Object column2Value = null;
					ColumnValue column2TileValue;
					if (column2 != null) {
						column2Value = tileRow.getValue(column2.getName());
						Class<?> column2ClassType = column2.getDataType()
								.getClassType();
						boolean column2Decimal = column2ClassType == Double.class
								|| column2ClassType == Float.class;
						if (column2Decimal) {
							column2TileValue = new ColumnValue(column2Value,
									.000001);
						} else {
							column2TileValue = new ColumnValue(column2Value);
						}
						fieldValues.put(column2.getName(), column2TileValue);
					}
					tileResultSet = dao.queryForValueFieldValues(fieldValues);
					TestCase.assertTrue(tileResultSet.getCount() > 0);
					found = false;
					while (tileResultSet.moveToNext()) {
						queryTileRow = tileResultSet.getRow();
						TestCase.assertEquals(column1Value,
								queryTileRow.getValue(column1.getName()));
						if (column2 != null) {
							TestCase.assertEquals(column2Value,
									queryTileRow.getValue(column2.getName()));
						}
						if (!found) {
							found = tileRow.getId() == queryTileRow.getId();
						}
					}
					TestCase.assertTrue(found);
					tileResultSet.close();
				}

				String previousColumn = null;
				for (String column : columns) {

					int expectedDistinctCount = dao
							.querySingleTypedResult(
									"SELECT COUNT(DISTINCT " + column
											+ ") FROM " + dao.getTableName(),
									null);
					int distinctCount = dao.count(true, column);
					TestCase.assertEquals(expectedDistinctCount, distinctCount);
					if (dao.count(column + " IS NULL") > 0) {
						distinctCount++;
					}
					TileResultSet expectedResultSet = dao
							.rawQuery("SELECT DISTINCT " + column + " FROM "
									+ dao.getTableName(), null);
					int expectedDistinctResultSetCount = expectedResultSet
							.getCount();
					int expectedDistinctManualResultSetCount = 0;
					while (expectedResultSet.moveToNext()) {
						expectedDistinctManualResultSetCount++;
					}
					expectedResultSet.close();
					TestCase.assertEquals(expectedDistinctManualResultSetCount,
							expectedDistinctResultSetCount);
					tileResultSet = dao.query(true, new String[] { column });
					TestCase.assertEquals(1, tileResultSet.getColumnCount());
					TestCase.assertEquals(expectedDistinctResultSetCount,
							tileResultSet.getCount());
					TestCase.assertEquals(distinctCount,
							tileResultSet.getCount());
					tileResultSet.close();
					tileResultSet = dao.query(new String[] { column });
					TestCase.assertEquals(1, tileResultSet.getColumnCount());
					TestCase.assertEquals(count, tileResultSet.getCount());
					Set<Object> distinctValues = new HashSet<>();
					while (tileResultSet.moveToNext()) {
						Object value = tileResultSet.getValue(column);
						distinctValues.add(value);
					}
					tileResultSet.close();
					if (!column
							.equals(tileTable.getTileDataColumn().getName())) {
						TestCase.assertEquals(distinctCount,
								distinctValues.size());
					}

					if (previousColumn != null) {

						tileResultSet = dao.query(true,
								new String[] { previousColumn, column });
						TestCase.assertEquals(2,
								tileResultSet.getColumnCount());
						distinctCount = tileResultSet.getCount();
						if (distinctCount < 0) {
							distinctCount = 0;
							while (tileResultSet.moveToNext()) {
								distinctCount++;
							}
						}
						tileResultSet.close();
						tileResultSet = dao
								.query(new String[] { previousColumn, column });
						TestCase.assertEquals(2,
								tileResultSet.getColumnCount());
						TestCase.assertEquals(count, tileResultSet.getCount());
						Map<Object, Set<Object>> distinctPairs = new HashMap<>();
						while (tileResultSet.moveToNext()) {
							Object previousValue = tileResultSet
									.getValue(previousColumn);
							Object value = tileResultSet.getValue(column);
							distinctValues = distinctPairs.get(previousValue);
							if (distinctValues == null) {
								distinctValues = new HashSet<>();
								distinctPairs.put(previousValue,
										distinctValues);
							}
							distinctValues.add(value);
						}
						tileResultSet.close();
						int distinctPairsCount = 0;
						for (Set<Object> values : distinctPairs.values()) {
							distinctPairsCount += values.size();
						}
						if (!previousColumn
								.equals(tileTable.getTileDataColumn().getName())
								&& !column.equals(tileTable.getTileDataColumn()
										.getName())) {
							TestCase.assertEquals(distinctCount,
									distinctPairsCount);
						}

					}

					previousColumn = column;
				}

			}
		}

	}

	/**
	 * Validate a tile row
	 * 
	 * @param dao
	 * @param columns
	 * @param tileRow
	 */
	private static void validateTileRow(TileDao dao, String[] columns,
			TileRow tileRow) {
		TestCase.assertEquals(columns.length, tileRow.columnCount());

		for (int i = 0; i < tileRow.columnCount(); i++) {
			TileColumn column = tileRow.getTable().getColumns().get(i);
			TestCase.assertEquals(i, column.getIndex());
			TestCase.assertEquals(columns[i], tileRow.getColumnName(i));
			TestCase.assertEquals(i, tileRow.getColumnIndex(columns[i]));
			int rowType = tileRow.getRowColumnType(i);
			Object value = tileRow.getValue(i);

			switch (rowType) {

			case ResultUtils.FIELD_TYPE_INTEGER:
				TestUtils.validateIntegerValue(value, column.getDataType());
				break;

			case ResultUtils.FIELD_TYPE_FLOAT:
				TestUtils.validateFloatValue(value, column.getDataType());
				break;

			case ResultUtils.FIELD_TYPE_STRING:
				TestCase.assertTrue(value instanceof String);
				break;

			case ResultUtils.FIELD_TYPE_BLOB:
				TestCase.assertTrue(value instanceof byte[]);
				break;

			case ResultUtils.FIELD_TYPE_NULL:
				TestCase.assertNull(value);
				break;

			}
		}

		TestCase.assertTrue(tileRow.getId() >= 0);
		TestCase.assertTrue(tileRow.getZoomLevel() >= 0);
		TestCase.assertTrue(tileRow.getTileColumn() >= 0);
		TestCase.assertTrue(tileRow.getTileRow() >= 0);
		byte[] tileData = tileRow.getTileData();
		TestCase.assertNotNull(tileData);
		TestCase.assertTrue(tileData.length > 0);

		TileMatrix tileMatrix = dao.getTileMatrix(tileRow.getZoomLevel());
		TestCase.assertNotNull(tileMatrix);

	}

	/**
	 * Test update
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 * @throws IOException
	 *             upon error
	 */
	public static void testUpdate(GeoPackage geoPackage)
			throws SQLException, IOException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				testUpdate(dao);

			}
		}

	}

	/**
	 * Test updates for the tile table
	 * 
	 * @param dao
	 *            tile dao
	 */
	public static void testUpdate(TileDao dao) {

		TestCase.assertNotNull(dao);

		TileResultSet resultSet = dao.queryForAll();
		int count = resultSet.getCount();
		if (count > 0) {

			// Choose random tile
			int random = (int) (Math.random() * count);
			resultSet.moveToPosition(random);

			String updatedString = null;
			String updatedLimitedString = null;
			Boolean updatedBoolean = null;
			Byte updatedByte = null;
			Short updatedShort = null;
			Integer updatedInteger = null;
			Long updatedLong = null;
			Float updatedFloat = null;
			Double updatedDouble = null;
			byte[] updatedBytes = null;
			byte[] updatedLimitedBytes = null;

			TileRow originalRow = resultSet.getRow();
			TileRow tileRow = resultSet.getRow();

			try {
				tileRow.setValue(tileRow.getPkColumnIndex(), 9);
				TestCase.fail("Updated the primary key value");
			} catch (GeoPackageException e) {
				// expected
			}

			for (TileColumn tileColumn : dao.getTable().getColumns()) {
				if (!tileColumn.isPrimaryKey()) {

					int rowColumnType = tileRow
							.getRowColumnType(tileColumn.getIndex());

					GeoPackageDataType dataType = tileColumn.getDataType();
					if (dataType == null) {
						continue;
					}

					switch (dataType) {
					case TEXT:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_STRING);
						if (updatedString == null) {
							updatedString = UUID.randomUUID().toString();
						}
						if (tileColumn.getMax() != null) {
							if (updatedLimitedString == null) {
								if (updatedString.length() > tileColumn
										.getMax()) {
									updatedLimitedString = updatedString
											.substring(0, tileColumn.getMax()
													.intValue());
								} else {
									updatedLimitedString = updatedString;
								}
							}
							tileRow.setValue(tileColumn.getIndex(),
									updatedLimitedString);
						} else {
							tileRow.setValue(tileColumn.getIndex(),
									updatedString);
						}
						break;
					case BOOLEAN:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_INTEGER);
						if (updatedBoolean == null) {
							Boolean existingValue = (Boolean) tileRow
									.getValue(tileColumn.getIndex());
							if (existingValue == null) {
								updatedBoolean = true;
							} else {
								updatedBoolean = !existingValue;
							}
						}
						tileRow.setValue(tileColumn.getIndex(), updatedBoolean);
						break;
					case TINYINT:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_INTEGER);
						if (updatedByte == null) {
							updatedByte = (byte) (((int) (Math.random()
									* (Byte.MAX_VALUE + 1)))
									* (Math.random() < .5 ? 1 : -1));
						}
						tileRow.setValue(tileColumn.getIndex(), updatedByte);
						break;
					case SMALLINT:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_INTEGER);
						if (updatedShort == null) {
							updatedShort = (short) (((int) (Math.random()
									* (Short.MAX_VALUE + 1)))
									* (Math.random() < .5 ? 1 : -1));
						}
						tileRow.setValue(tileColumn.getIndex(), updatedShort);
						break;
					case MEDIUMINT:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_INTEGER);
						if (updatedInteger == null) {
							updatedInteger = (int) (((int) (Math.random()
									* (Integer.MAX_VALUE + 1)))
									* (Math.random() < .5 ? 1 : -1));
						}
						tileRow.setValue(tileColumn.getIndex(), updatedInteger);
						break;
					case INT:
					case INTEGER:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_INTEGER);
						if (updatedLong == null) {
							updatedLong = (long) (((int) (Math.random()
									* (Long.MAX_VALUE + 1)))
									* (Math.random() < .5 ? 1 : -1));
						}
						tileRow.setValue(tileColumn.getIndex(), updatedLong);
						break;
					case FLOAT:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_FLOAT);
						if (updatedFloat == null) {
							updatedFloat = (float) Math.random()
									* Float.MAX_VALUE;
						}
						tileRow.setValue(tileColumn.getIndex(), updatedFloat);
						break;
					case DOUBLE:
					case REAL:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_FLOAT);
						if (updatedDouble == null) {
							updatedDouble = Math.random() * Double.MAX_VALUE;
						}
						tileRow.setValue(tileColumn.getIndex(), updatedDouble);
						break;
					case BLOB:
						validateRowColumnType(rowColumnType,
								ResultUtils.FIELD_TYPE_BLOB);
						if (updatedBytes == null) {
							updatedBytes = TestUtils.getTileBytes();
						}
						if (tileColumn.getMax() != null) {
							if (updatedLimitedBytes == null) {
								if (updatedBytes.length > tileColumn.getMax()) {
									updatedLimitedBytes = new byte[tileColumn
											.getMax().intValue()];
									ByteBuffer
											.wrap(updatedBytes, 0,
													tileColumn.getMax()
															.intValue())
											.get(updatedLimitedBytes);
								} else {
									updatedLimitedBytes = updatedBytes;
								}
							}
							tileRow.setValue(tileColumn.getIndex(),
									updatedLimitedBytes);
						} else {
							tileRow.setValue(tileColumn.getIndex(),
									updatedBytes);
						}
						break;
					default:
					}

				}
			}

			resultSet.close();
			TestCase.assertEquals(1, dao.update(tileRow));

			long id = tileRow.getId();
			TileRow readRow = dao.queryForIdRow(id);
			TestCase.assertNotNull(readRow);
			TestCase.assertEquals(originalRow.getId(), readRow.getId());

			for (String readColumnName : readRow.getColumnNames()) {

				TileColumn readTileColumn = readRow.getColumn(readColumnName);
				if (!readTileColumn.isPrimaryKey()) {
					switch (readRow.getRowColumnType(readColumnName)) {
					case ResultUtils.FIELD_TYPE_STRING:
						if (readTileColumn.getMax() != null) {
							TestCase.assertEquals(updatedLimitedString, readRow
									.getValue(readTileColumn.getIndex()));
						} else {
							TestCase.assertEquals(updatedString, readRow
									.getValue(readTileColumn.getIndex()));
						}
						break;
					case ResultUtils.FIELD_TYPE_INTEGER:
						switch (readTileColumn.getDataType()) {
						case BOOLEAN:
							TestCase.assertEquals(updatedBoolean, readRow
									.getValue(readTileColumn.getIndex()));
							break;
						case TINYINT:
							TestCase.assertEquals(updatedByte, readRow
									.getValue(readTileColumn.getIndex()));
							break;
						case SMALLINT:
							TestCase.assertEquals(updatedShort, readRow
									.getValue(readTileColumn.getIndex()));
							break;
						case MEDIUMINT:
							TestCase.assertEquals(updatedInteger, readRow
									.getValue(readTileColumn.getIndex()));
							break;
						case INT:
						case INTEGER:
							TestCase.assertEquals(updatedLong, readRow
									.getValue(readTileColumn.getIndex()));
							break;
						default:
							TestCase.fail("Unexpected integer type: "
									+ readTileColumn.getDataType());
						}
						break;
					case ResultUtils.FIELD_TYPE_FLOAT:
						switch (readTileColumn.getDataType()) {
						case FLOAT:
							TestCase.assertEquals(updatedFloat, readRow
									.getValue(readTileColumn.getIndex()));
							break;
						case DOUBLE:
						case REAL:
							TestCase.assertEquals(updatedDouble, readRow
									.getValue(readTileColumn.getIndex()));
							break;
						default:
							TestCase.fail("Unexpected integer type: "
									+ readTileColumn.getDataType());
						}
						break;
					case ResultUtils.FIELD_TYPE_BLOB:
						if (readTileColumn.getMax() != null) {
							GeoPackageGeometryDataUtils.compareByteArrays(
									updatedLimitedBytes,
									(byte[]) readRow.getValue(
											readTileColumn.getIndex()));
						} else {
							byte[] readBytes = (byte[]) readRow
									.getValue(readTileColumn.getIndex());
							GeoPackageGeometryDataUtils
									.compareByteArrays(updatedBytes, readBytes);
						}
						break;
					default:
					}
				}

			}

		}
		resultSet.close();

	}

	/**
	 * Validate the row type
	 * 
	 * @param rowColumnType
	 *            row column type
	 * @param expectedColumnType
	 *            expected column type
	 */
	private static void validateRowColumnType(int rowColumnType,
			int expectedColumnType) {
		if (rowColumnType == ResultUtils.FIELD_TYPE_NULL) {
			TestCase.fail(
					"Tile columns should all non nullable. Expected Column Type: "
							+ expectedColumnType);
		} else {
			TestCase.assertEquals(expectedColumnType, rowColumnType);
		}
	}

	/**
	 * Test create
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 */
	public static void testCreate(GeoPackage geoPackage) throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				TestCase.assertNotNull(dao);

				TileResultSet resultSet = dao.queryForAll();
				int count = resultSet.getCount();
				if (count > 0) {

					// Choose random tile
					int random = (int) (Math.random() * count);
					resultSet.moveToPosition(random);

					TileRow tileRow = resultSet.getRow();
					resultSet.close();

					// Find the largest zoom level
					TileMatrixDao tileMatrixDao = geoPackage.getTileMatrixDao();
					QueryBuilder<TileMatrix, TileMatrixKey> qb = tileMatrixDao
							.queryBuilder();
					qb.where().eq(TileMatrix.COLUMN_TABLE_NAME,
							tileMatrixSet.getTableName());
					qb.orderBy(TileMatrix.COLUMN_ZOOM_LEVEL, false);
					PreparedQuery<TileMatrix> query = qb.prepare();
					TileMatrix tileMatrix = tileMatrixDao.queryForFirst(query);
					long highestZoomLevel = tileMatrix.getZoomLevel();

					// Create new row from existing
					long id = tileRow.getId();
					tileRow.resetId();
					tileRow.setZoomLevel(highestZoomLevel + 1);
					long newRowId = dao.create(tileRow);
					TestCase.assertEquals(newRowId, tileRow.getId());

					// Verify original still exists and new was created
					tileRow = dao.queryForIdRow(id);
					TestCase.assertNotNull(tileRow);
					TileRow queryTileRow = dao.queryForIdRow(newRowId);
					TestCase.assertNotNull(queryTileRow);
					resultSet = dao.queryForAll();
					TestCase.assertEquals(count + 1, resultSet.getCount());
					resultSet.close();

					// Create new row with copied values from another
					TileRow newRow = dao.newRow();
					for (TileColumn column : dao.getTable().getColumns()) {

						if (column.isPrimaryKey()) {
							try {
								newRow.setValue(column.getName(), 10);
								TestCase.fail("Set primary key on new row");
							} catch (GeoPackageException e) {
								// Expected
							}
						} else {
							newRow.setValue(column.getName(),
									tileRow.getValue(column.getName()));
						}
					}

					newRow.setZoomLevel(queryTileRow.getZoomLevel() + 1);
					long newRowId2 = dao.create(newRow);

					TestCase.assertEquals(newRowId2, newRow.getId());

					// Verify new was created
					TileRow queryTileRow2 = dao.queryForIdRow(newRowId2);
					TestCase.assertNotNull(queryTileRow2);
					resultSet = dao.queryForAll();
					TestCase.assertEquals(count + 2, resultSet.getCount());
					resultSet.close();

					// Test copied row
					TileRow copyRow = queryTileRow2.copy();
					for (TileColumn column : dao.getTable().getColumns()) {
						if (column.getIndex() == queryTileRow2
								.getTileDataColumnIndex()) {
							byte[] tileData1 = queryTileRow2.getTileData();
							byte[] tileData2 = copyRow.getTileData();
							TestCase.assertNotSame(tileData1, tileData2);
							GeoPackageGeometryDataUtils
									.compareByteArrays(tileData1, tileData2);
						} else {
							TestCase.assertEquals(
									queryTileRow2.getValue(column.getName()),
									copyRow.getValue(column.getName()));
						}
					}

					copyRow.resetId();
					copyRow.setZoomLevel(queryTileRow2.getZoomLevel() + 1);

					long newRowId3 = dao.create(copyRow);

					TestCase.assertEquals(newRowId3, copyRow.getId());

					// Verify new was created
					TileRow queryTileRow3 = dao.queryForIdRow(newRowId3);
					TestCase.assertNotNull(queryTileRow3);
					resultSet = dao.queryForAll();
					TestCase.assertEquals(count + 3, resultSet.getCount());
					resultSet.close();

					for (TileColumn column : dao.getTable().getColumns()) {
						if (column.isPrimaryKey()) {
							TestCase.assertNotSame(queryTileRow2.getId(),
									queryTileRow3.getId());
						} else if (column.getIndex() == queryTileRow3
								.getZoomLevelColumnIndex()) {
							TestCase.assertEquals(queryTileRow2.getZoomLevel(),
									queryTileRow3.getZoomLevel() - 1);
						} else if (column.getIndex() == queryTileRow3
								.getTileDataColumnIndex()) {
							byte[] tileData1 = queryTileRow2.getTileData();
							byte[] tileData2 = queryTileRow3.getTileData();
							GeoPackageGeometryDataUtils
									.compareByteArrays(tileData1, tileData2);
						} else {
							TestCase.assertEquals(
									queryTileRow2.getValue(column.getName()),
									queryTileRow3.getValue(column.getName()));
						}
					}

				}
				resultSet.close();
			}
		}

	}

	/**
	 * Test delete
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 */
	public static void testDelete(GeoPackage geoPackage) throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				TestCase.assertNotNull(dao);

				TileResultSet resultSet = dao.queryForAll();
				int count = resultSet.getCount();
				if (count > 0) {

					// Choose random tile
					int random = (int) (Math.random() * count);
					resultSet.moveToPosition(random);

					TileRow tileRow = resultSet.getRow();
					resultSet.close();

					// Delete row
					TestCase.assertEquals(1, dao.delete(tileRow));

					// Verify deleted
					TileRow queryTileRow = dao.queryForIdRow(tileRow.getId());
					TestCase.assertNull(queryTileRow);
					resultSet = dao.queryForAll();
					TestCase.assertEquals(count - 1, resultSet.getCount());
					resultSet.close();
				}
				resultSet.close();
			}

		}
	}

	/**
	 * Test getZoomLevel
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 */
	public static void testGetZoomLevel(GeoPackage geoPackage)
			throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);

				List<TileMatrix> tileMatrices = dao.getTileMatrices();

				for (TileMatrix tileMatrix : tileMatrices) {

					double width = tileMatrix.getPixelXSize()
							* tileMatrix.getTileWidth();
					double height = tileMatrix.getPixelYSize()
							* tileMatrix.getTileHeight();

					long zoomLevel = dao.getZoomLevel(width);
					TestCase.assertEquals(tileMatrix.getZoomLevel(), zoomLevel);

					zoomLevel = dao.getZoomLevel(width, height);
					TestCase.assertEquals(tileMatrix.getZoomLevel(), zoomLevel);

					zoomLevel = dao.getZoomLevel(width + 1);
					TestCase.assertEquals(tileMatrix.getZoomLevel(), zoomLevel);

					zoomLevel = dao.getZoomLevel(width + 1, height + 1);
					TestCase.assertEquals(tileMatrix.getZoomLevel(), zoomLevel);

					zoomLevel = dao.getZoomLevel(width - 1);
					TestCase.assertEquals(tileMatrix.getZoomLevel(), zoomLevel);

					zoomLevel = dao.getZoomLevel(width - 1, height - 1);
					TestCase.assertEquals(tileMatrix.getZoomLevel(), zoomLevel);

				}

			}

		}

	}

	/**
	 * Test queryByRange
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 */
	public static void testQueryByRange(GeoPackage geoPackage)
			throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);

				List<TileMatrix> tileMatrices = dao.getTileMatrices();

				long mapMinZoom = Long.MAX_VALUE;
				long mapMaxZoom = Long.MIN_VALUE;
				long[] mapZoomRange = dao.getMapZoomRange();

				for (TileMatrix tileMatrix : tileMatrices) {

					double width = tileMatrix.getPixelXSize()
							* tileMatrix.getTileWidth();
					double height = tileMatrix.getPixelYSize()
							* tileMatrix.getTileHeight();

					long zoomLevel = dao.getZoomLevel(width, height);

					long mapZoom = dao.getMapZoom(tileMatrix.getZoomLevel());
					mapMinZoom = Math.min(mapMinZoom, mapZoom);
					mapMaxZoom = Math.max(mapMaxZoom, mapZoom);

					BoundingBox setProjectionBoundingBox = tileMatrixSet
							.getBoundingBox();
					BoundingBox setWebMercatorBoundingBox = setProjectionBoundingBox
							.transform(GeometryTransform.create(
									tileMatrixSet.getProjection(),
									ProjectionConstants.EPSG_WEB_MERCATOR));
					BoundingBox boundingBox = new BoundingBox(-180.0, -90.0,
							180.0, 90.0);
					BoundingBox webMercatorBoundingBox = boundingBox
							.transform(GeometryTransform.create(
									ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM,
									ProjectionConstants.EPSG_WEB_MERCATOR));

					TileGrid tileGrid = TileBoundingBoxUtils.getTileGrid(
							setWebMercatorBoundingBox,
							tileMatrix.getMatrixWidth(),
							tileMatrix.getMatrixHeight(),
							webMercatorBoundingBox);

					TileResultSet resultSet = dao.queryByTileGrid(tileGrid,
							zoomLevel);
					int resultSetCount = resultSet != null
							? resultSet.getCount()
							: 0;
					TileResultSet expectedResultSet = dao
							.queryForTile(zoomLevel);

					TestCase.assertEquals(expectedResultSet.getCount(),
							resultSetCount);
					if (resultSet != null) {
						resultSet.close();
					}
					expectedResultSet.close();

					double maxLon = (360.0 * Math.random()) - 180.0;
					double minLon = ((maxLon + 180.0) * Math.random()) - 180.0;
					double maxLat = (180.0 * Math.random()) - 90.0;
					double minLat = ((maxLon + 90.0) * Math.random()) - 90.0;
					boundingBox = new BoundingBox(minLon, minLat, maxLon,
							maxLat);
					webMercatorBoundingBox = boundingBox
							.transform(GeometryTransform.create(
									ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM,
									ProjectionConstants.EPSG_WEB_MERCATOR));
					tileGrid = TileBoundingBoxUtils.getTileGrid(
							setWebMercatorBoundingBox,
							tileMatrix.getMatrixWidth(),
							tileMatrix.getMatrixHeight(),
							webMercatorBoundingBox);
					resultSet = dao.queryByTileGrid(tileGrid, zoomLevel);
					resultSetCount = resultSet != null ? resultSet.getCount()
							: 0;

					if (tileGrid != null) {
						int count = 0;
						for (long column = tileGrid
								.getMinX(); column <= tileGrid
										.getMaxX(); column++) {
							for (long row = tileGrid.getMinY(); row <= tileGrid
									.getMaxY(); row++) {
								TileRow tileRow = dao.queryForTile(column, row,
										zoomLevel);
								if (tileRow != null) {
									count++;
								}
							}
						}
						TestCase.assertEquals(count, resultSetCount);
					} else {
						TestCase.assertEquals(0, resultSetCount);
					}
					if (resultSet != null) {
						resultSet.close();
					}

				}

				TestCase.assertEquals(mapZoomRange[0], mapMinZoom);
				TestCase.assertEquals(mapZoomRange[1], mapMaxZoom);

			}

		}

	}

	/**
	 * Test querying for the bounding box at a tile matrix zoom level
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 */
	public static void testTileMatrixBoundingBox(GeoPackage geoPackage)
			throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				TestCase.assertNotNull(dao);

				BoundingBox totalBoundingBox = tileMatrixSet.getBoundingBox();
				TestCase.assertEquals(totalBoundingBox, dao.getBoundingBox());

				List<TileMatrix> tileMatrices = dao.getTileMatrices();

				for (TileMatrix tileMatrix : tileMatrices) {

					double xDistance = tileMatrixSet.getMaxX()
							- tileMatrixSet.getMinX();
					double xDistance2 = tileMatrix.getMatrixWidth()
							* tileMatrix.getTileWidth()
							* tileMatrix.getPixelXSize();
					TestCase.assertEquals(xDistance, xDistance2, .0000000001);
					double yDistance = tileMatrixSet.getMaxY()
							- tileMatrixSet.getMinY();
					double yDistance2 = tileMatrix.getMatrixHeight()
							* tileMatrix.getTileHeight()
							* tileMatrix.getPixelYSize();
					TestCase.assertEquals(yDistance, yDistance2, .0000000001);

					long zoomLevel = tileMatrix.getZoomLevel();
					int count = dao.count(zoomLevel);
					TileGrid totalTileGrid = dao.getTileGrid(zoomLevel);
					TileGrid tileGrid = dao.queryForTileGrid(zoomLevel);
					BoundingBox boundingBox = dao.getBoundingBox(zoomLevel);

					if (totalTileGrid.equals(tileGrid)) {
						TestCase.assertEquals(totalBoundingBox, boundingBox);
					} else {
						TestCase.assertTrue(totalBoundingBox
								.getMinLongitude() <= boundingBox
										.getMinLongitude());
						TestCase.assertTrue(totalBoundingBox
								.getMaxLongitude() >= boundingBox
										.getMaxLongitude());
						TestCase.assertTrue(
								totalBoundingBox.getMinLatitude() <= boundingBox
										.getMinLatitude());
						TestCase.assertTrue(
								totalBoundingBox.getMaxLatitude() >= boundingBox
										.getMaxLatitude());
					}

					boolean minYDeleted = false;
					boolean maxYDeleted = false;
					boolean minXDeleted = false;
					boolean maxXDeleted = false;

					int deleted = 0;
					if (tileMatrix.getMatrixHeight() > 1
							|| tileMatrix.getMatrixWidth() > 1) {

						for (int column = 0; column < tileMatrix
								.getMatrixWidth(); column++) {
							int expectedDelete = dao.queryForTile(column, 0,
									zoomLevel) != null ? 1 : 0;
							TestCase.assertEquals(expectedDelete,
									dao.deleteTile(column, 0, zoomLevel));
							if (expectedDelete > 0) {
								minYDeleted = true;
							}
							deleted += expectedDelete;
							expectedDelete = dao.queryForTile(column,
									tileMatrix.getMatrixHeight() - 1,
									zoomLevel) != null ? 1 : 0;
							TestCase.assertEquals(expectedDelete,
									dao.deleteTile(column,
											tileMatrix.getMatrixHeight() - 1,
											zoomLevel));
							if (expectedDelete > 0) {
								maxYDeleted = true;
							}
							deleted += expectedDelete;
						}

						for (int row = 1; row < tileMatrix.getMatrixHeight()
								- 1; row++) {
							int expectedDelete = dao.queryForTile(0, row,
									zoomLevel) != null ? 1 : 0;
							TestCase.assertEquals(expectedDelete,
									dao.deleteTile(0, row, zoomLevel));
							if (expectedDelete > 0) {
								minXDeleted = true;
							}
							deleted += expectedDelete;
							expectedDelete = dao.queryForTile(
									tileMatrix.getMatrixWidth() - 1, row,
									zoomLevel) != null ? 1 : 0;
							TestCase.assertEquals(expectedDelete,
									dao.deleteTile(
											tileMatrix.getMatrixWidth() - 1,
											row, zoomLevel));
							if (expectedDelete > 0) {
								maxXDeleted = true;
							}
							deleted += expectedDelete;
						}
					} else {
						TestCase.assertEquals(1,
								dao.deleteTile(0, 0, zoomLevel));
						deleted++;
					}

					int updatedCount = dao.count(zoomLevel);
					TestCase.assertEquals(count - deleted, updatedCount);

					TileGrid updatedTileGrid = dao.queryForTileGrid(zoomLevel);
					BoundingBox updatedBoundingBox = dao
							.getBoundingBox(zoomLevel);

					if (updatedCount == 0 || (tileMatrix.getMatrixHeight() <= 2
							&& tileMatrix.getMatrixWidth() <= 2)) {
						TestCase.assertNull(updatedTileGrid);
						TestCase.assertNull(updatedBoundingBox);
					} else {
						TestCase.assertNotNull(updatedTileGrid);
						TestCase.assertNotNull(updatedBoundingBox);

						if (minXDeleted || minYDeleted || maxXDeleted
								|| maxYDeleted) {
							TestCase.assertTrue(updatedTileGrid
									.getMinX() >= tileGrid.getMinX());
							TestCase.assertTrue(updatedTileGrid
									.getMinY() >= tileGrid.getMinY());
							TestCase.assertTrue(updatedTileGrid
									.getMaxX() <= tileGrid.getMaxX());
							TestCase.assertTrue(updatedTileGrid
									.getMaxY() <= tileGrid.getMaxY());
						} else {
							TestCase.assertEquals(tileGrid.getMinX(),
									updatedTileGrid.getMinX());
							TestCase.assertEquals(tileGrid.getMinY(),
									updatedTileGrid.getMinY());
							TestCase.assertEquals(tileGrid.getMaxX(),
									updatedTileGrid.getMaxX());
							TestCase.assertEquals(tileGrid.getMaxY(),
									updatedTileGrid.getMaxY());
						}

						BoundingBox tileGridBoundingBox = TileBoundingBoxUtils
								.getBoundingBox(totalBoundingBox, tileMatrix,
										updatedTileGrid);
						TestCase.assertEquals(tileGridBoundingBox,
								updatedBoundingBox);
					}
				}

			}
		}

	}

	static boolean threadedTileDaoError = false;
	static boolean threadedTileDaoStop = false;

	/**
	 * Test threaded tile dao
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 * @throws SQLException
	 *             upon error
	 */
	public static void testThreadedTileDao(final GeoPackage geoPackage)
			throws SQLException {

		final int threads = 30;
		final int attemptsPerThread = 50;

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {

			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();
			for (TileMatrixSet tileMatrixSet : results) {

				threadedTileDaoError = false;
				threadedTileDaoStop = false;

				final String tableName = tileMatrixSet.getTableName();

				Runnable task = new Runnable() {

					@Override
					public void run() {
						for (int i = 0; i < attemptsPerThread; i++) {

							try {

								if (threadedTileDaoStop) {
									break;
								}

								ContentsDao contentsDao = geoPackage
										.getContentsDao();
								Contents contents = contentsDao
										.queryForId(tableName);
								if (contents == null) {
									throw new Exception(
											"Contents was null, table name: "
													+ tableName);
								}

								if (threadedTileDaoStop) {
									break;
								}

								TileDao dao = geoPackage.getTileDao(tableName);
								if (dao == null) {
									throw new Exception(
											"Tile DAO was null, table name: "
													+ tableName);
								}

							} catch (Exception e) {
								threadedTileDaoError = true;
								e.printStackTrace();
								break;
							}
						}
					}
				};

				ExecutorService executor = Executors
						.newFixedThreadPool(threads);
				for (int i = 0; i < threads; i++) {
					executor.submit(task);
				}

				executor.shutdown();
				try {
					executor.awaitTermination(60, TimeUnit.SECONDS);
					threadedTileDaoStop = true;
				} catch (InterruptedException e) {
					e.printStackTrace();
					TestCase.fail("Waiting for threads terminated: "
							+ e.getMessage());
				}

				try {
					executor.awaitTermination(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
					TestCase.fail("Waiting for threads terminated: "
							+ e.getMessage());
				}

				if (threadedTileDaoError) {
					TestCase.fail("Error occurred during threading");
				}

			}

		}
	}

}
