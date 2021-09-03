/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company and XSK contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-FileCopyrightText: 2021 SAP SE or an SAP affiliate company and XSK contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.xsk.hdb.ds.processors.hdbstructure;

import static java.text.MessageFormat.format;

import com.sap.xsk.hdb.ds.api.IXSKDataStructureModel;
import com.sap.xsk.hdb.ds.model.hdbtabletype.XSKDataStructureHDBTableTypeModel;
import com.sap.xsk.hdb.ds.module.XSKHDBModule;
import com.sap.xsk.hdb.ds.processors.AbstractXSKProcessor;
import com.sap.xsk.hdb.ds.service.manager.IXSKDataStructureManager;
import com.sap.xsk.utils.XSKHDBUtils;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.eclipse.dirigible.database.sql.DatabaseArtifactTypes;
import org.eclipse.dirigible.database.sql.ISqlDialect;
import org.eclipse.dirigible.database.sql.SqlFactory;
import org.eclipse.dirigible.database.sql.dialects.hana.HanaSqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XSKTableTypeDropProcessor extends AbstractXSKProcessor<XSKDataStructureHDBTableTypeModel> {

  private static final Logger logger = LoggerFactory.getLogger(XSKTableTypeDropProcessor.class);
  private Map<String, IXSKDataStructureManager> managerServices = XSKHDBModule.getManagerServices();

  @Override
  public void execute(Connection connection, XSKDataStructureHDBTableTypeModel tableTypeModel) throws SQLException {
    logger.info("Processing Drop Table Type: " + tableTypeModel.getName());

    //drop public synonym
    if (managerServices != null) {
      XSKHDBUtils.dropPublicSynonymForArtifact(managerServices
          .get(IXSKDataStructureModel.TYPE_HDB_SYNONYM), tableTypeModel.getName(), tableTypeModel.getSchema(), connection);
    }

    //drop table type
    ISqlDialect dialect = SqlFactory.deriveDialect(connection);
    if (!(dialect.getClass().equals(HanaSqlDialect.class))) {
      throw new IllegalStateException(String.format("XSK does not support Table Type for %s yet", dialect.getDatabaseName(connection)));
    } else {
      if (SqlFactory.getNative(connection).exists(connection, tableTypeModel.getName(), DatabaseArtifactTypes.TABLE_TYPE)) {
        String tableTypeName = XSKHDBUtils.escapeArtifactName(connection, tableTypeModel.getName());
        String sql = SqlFactory.getNative(connection).drop().tableType(tableTypeName).build();
        executeSql(sql, connection);
      } else {
        logger.warn(format("Table Type [{0}] does not exists during the drop process", tableTypeModel.getName()));
      }
    }
  }
}