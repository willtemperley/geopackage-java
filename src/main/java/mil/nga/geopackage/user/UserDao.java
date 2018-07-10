package mil.nga.geopackage.user;

import java.sql.Connection;

import mil.nga.geopackage.db.GeoPackageConnection;
import mil.nga.geopackage.db.SQLUtils;

/**
 * Abstract User DAO for reading user tables
 * 
 * @param <TColumn>
 *            column type
 * @param <TTable>
 *            table type
 * @param <TRow>
 *            row type
 * @param <TResult>
 *            result type
 * 
 * @author osbornb
 */
public abstract class UserDao<TColumn extends UserColumn, TTable extends UserTable<TColumn>, TRow extends UserRow<TColumn, TTable>, TResult extends UserResultSet<TColumn, TTable, TRow>>
		extends UserCoreDao<TColumn, TTable, TRow, TResult> {

	/**
	 * Connection
	 */
	private final Connection connection;

	/**
	 * Constructor
	 * 
	 * @param database
	 *            database
	 * @param db
	 *            GeoPackage connection
	 * @param userDb
	 *            user connection
	 * @param table
	 *            table
	 */
	protected UserDao(String database, GeoPackageConnection db,
			UserConnection<TColumn, TTable, TRow, TResult> userDb, TTable table) {
		super(database, db, userDb, table);
		this.connection = db.getConnection();
		userDb.setTable(table);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public GeoPackageConnection getDb() {
		return (GeoPackageConnection) super.getDb();
	}

	/**
	 * Get the database connection
	 * 
	 * @return connection
	 */
	public Connection getConnection() {
		return connection;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected TResult prepareResult(TResult result) {
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int update(TRow row) {
		ContentValues contentValues = row.toContentValues();
		int updated = 0;
		if (contentValues.size() > 0) {
			updated = SQLUtils.update(connection, getTableName(),
					contentValues, getPkWhere(row.getId()),
					getPkWhereArgs(row.getId()));
		}
		return updated;
	}

	/**
	 * Update all rows matching the where clause with the provided values
	 * 
	 * @param values
	 *            content values
	 * @param whereClause
	 *            where clause
	 * @param whereArgs
	 *            where arguments
	 * @return updated count
	 */
	public int update(ContentValues values, String whereClause,
			String[] whereArgs) {
		return SQLUtils.update(connection, getTableName(), values, whereClause,
				whereArgs);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long insert(TRow row) {
		long id = SQLUtils.insertOrThrow(connection, getTableName(),
				row.toContentValues());
		if (row.hasIdColumn()) {
			row.setId(id);
		}
		return id;
	}

	/**
	 * Inserts a new row
	 * 
	 * @param values
	 *            content values
	 * @return row id, -1 on error
	 */
	public long insert(ContentValues values) {
		return SQLUtils.insert(connection, getTableName(), values);
	}

	/**
	 * Inserts a new row
	 * 
	 * @param values
	 *            content values
	 * @return row id
	 */
	public long insertOrThrow(ContentValues values) {
		return SQLUtils.insertOrThrow(connection, getTableName(), values);
	}

}
