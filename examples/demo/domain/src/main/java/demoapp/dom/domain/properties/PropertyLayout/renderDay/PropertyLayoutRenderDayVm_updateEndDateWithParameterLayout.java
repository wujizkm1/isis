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
package demoapp.dom.domain.properties.PropertyLayout.renderDay;

import java.time.LocalDate;

import org.apache.isis.applib.annotation.Action;
import org.apache.isis.applib.annotation.Optionality;
import org.apache.isis.applib.annotation.Parameter;
import org.apache.isis.applib.annotation.ParameterLayout;
import org.apache.isis.applib.annotation.RenderDay;
import org.apache.isis.applib.annotation.SemanticsOf;

import lombok.RequiredArgsConstructor;

@Action(
    semantics = SemanticsOf.IDEMPOTENT,
    associateWith = "endDate", associateWithSequence = "1"
)
@RequiredArgsConstructor
public class PropertyLayoutRenderDayVm_updateEndDateWithParameterLayout {

    private final PropertyLayoutRenderDayVm propertyLayoutRenderDayVm;

//tag::annotation[]
    public PropertyLayoutRenderDayVm act(
            @Parameter(optionality = Optionality.OPTIONAL)
            @ParameterLayout(
                renderDay = RenderDay.AS_DAY_BEFORE             // <.>
                , describedAs =
                    "@ParameterLayout(renderDay = AS_DAY_BEFORE)"
            )
            final LocalDate endDate) {
        propertyLayoutRenderDayVm.setEndDate(endDate);
        return propertyLayoutRenderDayVm;
    }
//end::annotation[]
    public LocalDate default0Act() {
        return propertyLayoutRenderDayVm.getEndDate();
    }

}