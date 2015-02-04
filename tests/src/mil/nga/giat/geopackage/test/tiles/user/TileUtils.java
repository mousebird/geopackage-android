package mil.nga.giat.geopackage.test.tiles.user;

import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;
import mil.nga.giat.geopackage.GeoPackage;
import mil.nga.giat.geopackage.GeoPackageException;
import mil.nga.giat.geopackage.test.TestSetupTeardown;
import mil.nga.giat.geopackage.test.TestUtils;
import mil.nga.giat.geopackage.test.geom.GeoPackageGeometryDataUtils;
import mil.nga.giat.geopackage.tiles.matrixset.TileMatrixSet;
import mil.nga.giat.geopackage.tiles.matrixset.TileMatrixSetDao;
import mil.nga.giat.geopackage.tiles.user.TileColumn;
import mil.nga.giat.geopackage.tiles.user.TileCursor;
import mil.nga.giat.geopackage.tiles.user.TileDao;
import mil.nga.giat.geopackage.tiles.user.TileRow;
import mil.nga.giat.geopackage.tiles.user.TileTable;
import mil.nga.giat.geopackage.user.ColumnValue;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;

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
	 * @throws SQLException
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
				TestCase.assertEquals(tileMatrixSet.getId(), dao
						.getTileMatrixSet().getId());
				TestCase.assertEquals(tileMatrixSet.getTableName(),
						dao.getTableName());
				TestCase.assertFalse(dao.getTileMatrix().isEmpty());

				TileTable tileTable = dao.getTable();
				String[] columns = tileTable.getColumnNames();
				int zoomLevelIndex = tileTable.getZoomLevelColumnIndex();
				TestCase.assertTrue(zoomLevelIndex >= 0
						&& zoomLevelIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_ZOOM_LEVEL,
						columns[zoomLevelIndex]);
				int tileColumnIndex = tileTable.getTileColumnColumnIndex();
				TestCase.assertTrue(tileColumnIndex >= 0
						&& tileColumnIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_TILE_COLUMN,
						columns[tileColumnIndex]);
				int tileRowIndex = tileTable.getTileRowColumnIndex();
				TestCase.assertTrue(tileRowIndex >= 0
						&& tileRowIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_TILE_ROW,
						columns[tileRowIndex]);
				int tileDataIndex = tileTable.getTileDataColumnIndex();
				TestCase.assertTrue(tileDataIndex >= 0
						&& tileDataIndex < columns.length);
				TestCase.assertEquals(TileTable.COLUMN_TILE_DATA,
						columns[tileDataIndex]);

				// Query for all
				TileCursor cursor = dao.queryForAll();
				int count = cursor.getCount();
				int manualCount = 0;
				while (cursor.moveToNext()) {

					TileRow tileRow = cursor.getRow();
					validateTileRow(columns, tileRow);

					manualCount++;
				}
				TestCase.assertEquals(count, manualCount);
				cursor.close();

				// Manually query for all and compare
				cursor = (TileCursor) dao.getDb().query(dao.getTableName(),
						null, null, null, null, null, null);
				count = cursor.getCount();
				manualCount = 0;
				while (cursor.moveToNext()) {
					manualCount++;
				}
				TestCase.assertEquals(count, manualCount);

				TestCase.assertTrue("No tiles to test", count > 0);

				// Choose random tile
				int random = (int) (Math.random() * count);
				cursor.moveToPosition(random);
				TileRow tileRow = cursor.getRow();

				cursor.close();

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
					cursor = dao
							.queryForEq(column1.getName(), column1TileValue);
					TestCase.assertTrue(cursor.getCount() > 0);
					boolean found = false;
					while (cursor.moveToNext()) {
						queryTileRow = cursor.getRow();
						TestCase.assertEquals(column1Value,
								queryTileRow.getValue(column1.getName()));
						if (!found) {
							found = tileRow.getId() == queryTileRow.getId();
						}
					}
					TestCase.assertTrue(found);
					cursor.close();

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
					cursor = dao.queryForValueFieldValues(fieldValues);
					TestCase.assertTrue(cursor.getCount() > 0);
					found = false;
					while (cursor.moveToNext()) {
						queryTileRow = cursor.getRow();
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
					cursor.close();
				}
			}
		}

	}

	/**
	 * Validate a tile row
	 * 
	 * @param columns
	 * @param tileRow
	 */
	private static void validateTileRow(String[] columns, TileRow tileRow) {
		TestCase.assertEquals(columns.length, tileRow.columnCount());

		for (int i = 0; i < tileRow.columnCount(); i++) {
			TileColumn column = tileRow.getTable().getColumns().get(i);
			TestCase.assertEquals(i, column.getIndex());
			TestCase.assertEquals(columns[i], tileRow.getColumnName(i));
			TestCase.assertEquals(i, tileRow.getColumnIndex(columns[i]));
			int rowType = tileRow.getRowColumnType(i);
			Object value = tileRow.getValue(i);

			switch (rowType) {

			case Cursor.FIELD_TYPE_INTEGER:
				TestUtils.validateIntegerValue(value, column.getDataType());
				break;

			case Cursor.FIELD_TYPE_FLOAT:
				TestUtils.validateFloatValue(value, column.getDataType());
				break;

			case Cursor.FIELD_TYPE_STRING:
				TestCase.assertTrue(value instanceof String);
				break;

			case Cursor.FIELD_TYPE_BLOB:
				TestCase.assertTrue(value instanceof byte[]);
				break;

			case Cursor.FIELD_TYPE_NULL:
				TestCase.assertNull(value);
				break;

			}
		}
	}

	/**
	 * Test update
	 * 
	 * @param geoPackage
	 * @throws SQLException
	 */
	public static void testUpdate(GeoPackage geoPackage) throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				TestCase.assertNotNull(dao);

				TileCursor cursor = dao.queryForAll();
				int count = cursor.getCount();
				if (count > 0) {

					// Choose random tile
					int random = (int) (Math.random() * count);
					cursor.moveToPosition(random);

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

					TileRow originalRow = cursor.getRow();
					TileRow tileRow = cursor.getRow();

					try {
						tileRow.setValue(tileRow.getPkColumnIndex(), 9);
						TestCase.fail("Updated the primary key value");
					} catch (GeoPackageException e) {
						// expected
					}

					for (TileColumn tileColumn : dao.getTable().getColumns()) {
						if (!tileColumn.isPrimaryKey()) {

							switch (tileRow.getRowColumnType(tileColumn
									.getIndex())) {

							case Cursor.FIELD_TYPE_STRING:
								if (updatedString == null) {
									updatedString = UUID.randomUUID()
											.toString();
								}
								if (tileColumn.getMax() != null) {
									if (updatedLimitedString != null) {
										if (updatedString.length() > tileColumn
												.getMax()) {
											updatedLimitedString = updatedString
													.substring(0, tileColumn
															.getMax()
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
							case Cursor.FIELD_TYPE_INTEGER:
								switch (tileColumn.getDataType()) {
								case BOOLEAN:
									if (updatedBoolean == null) {
										updatedBoolean = !((Boolean) tileRow
												.getValue(tileColumn.getIndex()));
									}
									tileRow.setValue(tileColumn.getIndex(),
											updatedBoolean);
									break;
								case TINYINT:
									if (updatedByte == null) {
										updatedByte = (byte) (((int) (Math
												.random() * (Byte.MAX_VALUE + 1))) * (Math
												.random() < .5 ? 1 : -1));
									}
									tileRow.setValue(tileColumn.getIndex(),
											updatedByte);
									break;
								case SMALLINT:
									if (updatedShort == null) {
										updatedShort = (short) (((int) (Math
												.random() * (Short.MAX_VALUE + 1))) * (Math
												.random() < .5 ? 1 : -1));
									}
									tileRow.setValue(tileColumn.getIndex(),
											updatedShort);
									break;
								case MEDIUMINT:
									if (updatedInteger == null) {
										updatedInteger = (int) (((int) (Math
												.random() * (Integer.MAX_VALUE + 1))) * (Math
												.random() < .5 ? 1 : -1));
									}
									tileRow.setValue(tileColumn.getIndex(),
											updatedInteger);
									break;
								case INT:
								case INTEGER:
									if (updatedLong == null) {
										updatedLong = (long) (((int) (Math
												.random() * (Long.MAX_VALUE + 1))) * (Math
												.random() < .5 ? 1 : -1));
									}
									tileRow.setValue(tileColumn.getIndex(),
											updatedLong);
									break;
								default:
									TestCase.fail("Unexpected integer type: "
											+ tileColumn.getDataType());
								}
								break;
							case Cursor.FIELD_TYPE_FLOAT:
								switch (tileColumn.getDataType()) {
								case FLOAT:
									if (updatedFloat == null) {
										updatedFloat = (float) Math.random()
												* Float.MAX_VALUE;
									}
									tileRow.setValue(tileColumn.getIndex(),
											updatedFloat);
									break;
								case DOUBLE:
								case REAL:
									if (updatedDouble == null) {
										updatedDouble = Math.random()
												* Double.MAX_VALUE;
									}
									tileRow.setValue(tileColumn.getIndex(),
											updatedDouble);
									break;
								default:
									TestCase.fail("Unexpected float type: "
											+ tileColumn.getDataType());
								}
								break;
							case Cursor.FIELD_TYPE_BLOB:
								if (updatedBytes == null) {
									updatedBytes = UUID.randomUUID().toString()
											.getBytes();
								}
								if (tileColumn.getMax() != null) {
									if (updatedLimitedBytes != null) {
										if (updatedBytes.length > tileColumn
												.getMax()) {
											updatedLimitedBytes = new byte[tileColumn
													.getMax().intValue()];
											ByteBuffer.wrap(
													updatedBytes,
													0,
													tileColumn.getMax()
															.intValue()).get(
													updatedLimitedBytes);
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

					cursor.close();
					try {
						TestCase.assertEquals(1, dao.update(tileRow));
					} catch (SQLiteException e) {
						if (TestUtils.isFutureSQLiteException(e)) {
							continue;
						} else {
							throw e;
						}
					}

					long id = tileRow.getId();
					TileRow readRow = dao.queryForIdRow(id);
					TestCase.assertNotNull(readRow);
					TestCase.assertEquals(originalRow.getId(), readRow.getId());

					for (String readColumnName : readRow.getColumnNames()) {

						TileColumn readTileColumn = readRow
								.getColumn(readColumnName);
						if (!readTileColumn.isPrimaryKey()) {
							switch (readRow.getRowColumnType(readColumnName)) {
							case Cursor.FIELD_TYPE_STRING:
								if (readTileColumn.getMax() != null) {
									TestCase.assertEquals(updatedLimitedString,
											readRow.getValue(readTileColumn
													.getIndex()));
								} else {
									TestCase.assertEquals(updatedString,
											readRow.getValue(readTileColumn
													.getIndex()));
								}
								break;
							case Cursor.FIELD_TYPE_INTEGER:
								switch (readTileColumn.getDataType()) {
								case BOOLEAN:
									TestCase.assertEquals(updatedBoolean,
											readRow.getValue(readTileColumn
													.getIndex()));
									break;
								case TINYINT:
									TestCase.assertEquals(updatedByte,
											readRow.getValue(readTileColumn
													.getIndex()));
									break;
								case SMALLINT:
									TestCase.assertEquals(updatedShort,
											readRow.getValue(readTileColumn
													.getIndex()));
									break;
								case MEDIUMINT:
									TestCase.assertEquals(updatedInteger,
											readRow.getValue(readTileColumn
													.getIndex()));
									break;
								case INT:
								case INTEGER:
									TestCase.assertEquals(updatedLong,
											readRow.getValue(readTileColumn
													.getIndex()));
									break;
								default:
									TestCase.fail("Unexpected integer type: "
											+ readTileColumn.getDataType());
								}
								break;
							case Cursor.FIELD_TYPE_FLOAT:
								switch (readTileColumn.getDataType()) {
								case FLOAT:
									TestCase.assertEquals(updatedFloat,
											readRow.getValue(readTileColumn
													.getIndex()));
									break;
								case DOUBLE:
								case REAL:
									TestCase.assertEquals(updatedDouble,
											readRow.getValue(readTileColumn
													.getIndex()));
									break;
								default:
									TestCase.fail("Unexpected integer type: "
											+ readTileColumn.getDataType());
								}
								break;
							case Cursor.FIELD_TYPE_BLOB:
								if (readTileColumn.getMax() != null) {
									GeoPackageGeometryDataUtils
											.compareByteArrays(
													updatedLimitedBytes,
													(byte[]) readRow
															.getValue(readTileColumn
																	.getIndex()));
								} else {
									GeoPackageGeometryDataUtils
											.compareByteArrays(
													updatedBytes,
													(byte[]) readRow
															.getValue(readTileColumn
																	.getIndex()));
								}
								break;
							default:
							}
						}

					}

				}
				cursor.close();
			}
		}

	}

	/**
	 * Test create
	 * 
	 * @param geoPackage
	 * @throws SQLException
	 */
	public static void testCreate(GeoPackage geoPackage) throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				TestCase.assertNotNull(dao);

				TileCursor cursor = dao.queryForAll();
				int count = cursor.getCount();
				if (count > 0) {

					// Choose random tile
					int random = (int) (Math.random() * count);
					cursor.moveToPosition(random);

					TileRow tileRow = cursor.getRow();
					cursor.close();

					// Create new row from existing
					long id = tileRow.getId();
					tileRow.resetId();
					tileRow.setZoomLevel(TestSetupTeardown.CREATE_TILE_MATRIX_COUNT);
					long newRowId;
					try {
						newRowId = dao.create(tileRow);
					} catch (SQLiteException e) {
						if (TestUtils.isFutureSQLiteException(e)) {
							continue;
						} else {
							throw e;
						}
					}
					TestCase.assertEquals(newRowId, tileRow.getId());

					// Verify original still exists and new was created
					tileRow = dao.queryForIdRow(id);
					TestCase.assertNotNull(tileRow);
					TileRow queryTileRow = dao.queryForIdRow(newRowId);
					TestCase.assertNotNull(queryTileRow);
					cursor = dao.queryForAll();
					TestCase.assertEquals(count + 1, cursor.getCount());
					cursor.close();

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
					long newRowId2;
					try {
						newRowId2 = dao.create(newRow);
					} catch (SQLiteException e) {
						if (TestUtils.isFutureSQLiteException(e)) {
							continue;
						} else {
							throw e;
						}
					}
					TestCase.assertEquals(newRowId2, newRow.getId());

					// Verify new was created
					TileRow queryTileRow2 = dao.queryForIdRow(newRowId2);
					TestCase.assertNotNull(queryTileRow2);
					cursor = dao.queryForAll();
					TestCase.assertEquals(count + 2, cursor.getCount());
					cursor.close();
				}
				cursor.close();
			}
		}

	}

	/**
	 * Test delete
	 * 
	 * @param geoPackage
	 * @throws SQLException
	 */
	public static void testDelete(GeoPackage geoPackage) throws SQLException {

		TileMatrixSetDao tileMatrixSetDao = geoPackage.getTileMatrixSetDao();

		if (tileMatrixSetDao.isTableExists()) {
			List<TileMatrixSet> results = tileMatrixSetDao.queryForAll();

			for (TileMatrixSet tileMatrixSet : results) {

				TileDao dao = geoPackage.getTileDao(tileMatrixSet);
				TestCase.assertNotNull(dao);

				TileCursor cursor = dao.queryForAll();
				int count = cursor.getCount();
				if (count > 0) {

					// Choose random tile
					int random = (int) (Math.random() * count);
					cursor.moveToPosition(random);

					TileRow tileRow = cursor.getRow();
					cursor.close();

					// Delete row
					try {
						TestCase.assertEquals(1, dao.delete(tileRow));
					} catch (SQLiteException e) {
						if (TestUtils.isFutureSQLiteException(e)) {
							continue;
						} else {
							throw e;
						}
					}

					// Verify deleted
					TileRow queryTileRow = dao.queryForIdRow(tileRow.getId());
					TestCase.assertNull(queryTileRow);
					cursor = dao.queryForAll();
					TestCase.assertEquals(count - 1, cursor.getCount());
					cursor.close();
				}
				cursor.close();
			}

		}
	}

}