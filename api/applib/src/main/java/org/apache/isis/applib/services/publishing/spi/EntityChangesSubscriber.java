/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.isis.applib.services.publishing.spi;

import org.apache.isis.commons.having.HasEnabling;

/**
 * Part of the <i>Publishing SPI</i>. A component to receive the entire set of entities 
 * (with publishing enabled) that are about to change, serializable as ChangesDto.
 *  
 * 
 * @since 2.0 {@index}
 */
public interface EntityChangesSubscriber extends HasEnabling {

    /**
     * Receives all changing entities (with publishing enabled) at then end of the a 
     * transaction during the pre-commit phase.
     */
    void onChanging(EntityChanges entityChanges);
}
