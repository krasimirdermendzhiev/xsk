/*
 * Copyright (c) 2019-2021 SAP SE or an SAP affiliate company and XSK contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-FileCopyrightText: 2019-2021 SAP SE or an SAP affiliate company and XSK contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.xsk.hdb.ds.processors.table.utils;

import com.sap.xsk.hdb.ds.model.hdbtable.XSKDataStructureHDBTableColumnModel;
import com.sap.xsk.hdb.ds.model.hdbtable.XSKDataStructureHDBTableModel;
import com.sap.xsk.utils.XSKHDBUtils;
import org.eclipse.dirigible.commons.config.Configuration;
import org.eclipse.dirigible.database.ds.model.IDataStructureModel;
import org.eclipse.dirigible.database.sql.DataType;
import org.eclipse.dirigible.database.sql.DataTypeUtils;
import org.eclipse.dirigible.database.sql.ISqlKeywords;
import org.eclipse.dirigible.database.sql.SqlFactory;
import org.eclipse.dirigible.database.sql.builders.table.AlterTableBuilder;
import org.eclipse.dirigible.databases.helpers.DatabaseMetadataHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XSKTableAlterColumnHandler {

  private static final Logger logger = LoggerFactory.getLogger(XSKTableAlterColumnHandler.class);
  private static final String INCOMPATIBLE_CHANGE_OF_TABLE = "Incompatible change of table [%s] by adding a column [%s] which is [%s]"; //$NON-NLS-1$
  XSKDataStructureHDBTableModel tableModel;
  private Map<String, String> dbColumnDefinitions;
  private List<String> modelColumnNames;

  public XSKTableAlterColumnHandler(Connection connection, XSKDataStructureHDBTableModel tableModel) throws SQLException {
    this.dbColumnDefinitions = new HashMap<String, String>();

    DatabaseMetaData dmd = connection.getMetaData();
    ResultSet rsColumns = dmd
        .getColumns(null, null, DatabaseMetadataHelper.normalizeTableName(XSKHDBUtils.escapeArtifactName(connection, tableModel.getName())),
            null);
    while (rsColumns.next()) {
      String typeName = DataTypeUtils.getDatabaseTypeName(rsColumns.getInt(5));
      this.dbColumnDefinitions.put(rsColumns.getString(4), typeName);
    }
    this.modelColumnNames = tableModel.getColumns().stream().map(column -> column.getName()).collect(Collectors.toList());
    this.tableModel = tableModel;
  }

  public void addColumns(Connection connection, XSKDataStructureHDBTableModel tableModel) throws SQLException {
    String tableName = XSKHDBUtils.escapeArtifactName(connection, tableModel.getName());
    // ADD iteration
    for (XSKDataStructureHDBTableColumnModel columnModel : tableModel.getColumns()) {
      String name = columnModel.getName();
      DataType type = DataType.valueOf(columnModel.getType());
      String length = columnModel.getLength();
      boolean isNullable = columnModel.isNullable();
      boolean isPrimaryKey = columnModel.isPrimaryKey();
      boolean isUnique = columnModel.isUnique();
      String defaultValue = columnModel.getDefaultValue();
      String precision = columnModel.getPrecision();
      String scale = columnModel.getScale();
      String args = "";
      if (length != null) {
        if (type.equals(DataType.VARCHAR) || type.equals(DataType.CHAR) || type.equals(DataType.NVARCHAR)) {
          args = ISqlKeywords.OPEN + length + ISqlKeywords.CLOSE;
        }
      } else if ((precision != null) && (scale != null)) {
        if (type.equals(DataType.DECIMAL)) {
          args = ISqlKeywords.OPEN + precision + "," + scale + ISqlKeywords.CLOSE;
        }
      }
      if (defaultValue != null) {
        if (type.equals(DataType.VARCHAR) || type.equals(DataType.CHAR) || type.equals(DataType.NVARCHAR)) {
          args += " DEFAULT '" + defaultValue + "' ";
        } else {
          args += " DEFAULT " + defaultValue + " ";
        }
      }

      if (!dbColumnDefinitions.containsKey(name)) {

        AlterTableBuilder alterTableBuilder = SqlFactory.getNative(connection).alter().table(tableName);

        alterTableBuilder.add().column(XSKHDBUtils.escapeArtifactName(connection, name), type, isPrimaryKey, isNullable, isUnique, args);

        if (!isNullable) {
          throw new SQLException(String.format(INCOMPATIBLE_CHANGE_OF_TABLE, tableName, name, "NOT NULL"));
        }
        if (isPrimaryKey) {
          throw new SQLException(String.format(INCOMPATIBLE_CHANGE_OF_TABLE, tableName, name, "PRIMARY KEY"));
        }

        executeAlterBuilder(connection, alterTableBuilder);

      } else if (!dbColumnDefinitions.get(name).equals(type.toString())) {
        throw new SQLException(String
            .format(INCOMPATIBLE_CHANGE_OF_TABLE, tableName, name,
                "of type " + dbColumnDefinitions.get(name) + " to be changed to" + type));
      }
    }
  }

  public void removeColumns(Connection connection) throws SQLException {
    boolean caseSensitive = Boolean.parseBoolean(Configuration.get(IDataStructureModel.DIRIGIBLE_DATABASE_NAMES_CASE_SENSITIVE, "false"));
    for (String columnName : this.dbColumnDefinitions.keySet()) {
      if (!modelColumnNames.contains(columnName)) {
        AlterTableBuilder alterTableBuilder = SqlFactory.getNative(connection).alter()
            .table(XSKHDBUtils.escapeArtifactName(connection, tableModel.getName()));
        if (caseSensitive) {
          columnName = "\"" + columnName + "\"";
        }
        alterTableBuilder.drop().column(columnName, DataType.BOOLEAN);
        executeAlterBuilder(connection, alterTableBuilder);
      }
    }
  }

  private void executeAlterBuilder(Connection connection, AlterTableBuilder alterTableBuilder)
      throws SQLException {
    final String sql = alterTableBuilder.build();
    logger.info(sql);
    PreparedStatement statement = connection.prepareStatement(sql);
    try {
      statement.executeUpdate();
    } catch (SQLException e) {
      logger.error(sql);
      logger.error(e.getMessage(), e);
      throw new SQLException(e.getMessage(), e);
    } finally {
      if (statement != null) {
        statement.close();
      }
    }
  }
}
