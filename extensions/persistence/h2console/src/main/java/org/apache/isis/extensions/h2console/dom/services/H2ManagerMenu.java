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
package org.apache.isis.extensions.h2console.dom.services;

import javax.inject.Inject;

import org.apache.isis.applib.IsisApplibModule;
import org.apache.isis.applib.annotation.Action;
import org.apache.isis.applib.annotation.ActionLayout;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.DomainServiceLayout;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.RestrictTo;
import org.apache.isis.applib.annotation.SemanticsOf;
import org.apache.isis.applib.services.registry.ServiceRegistry;
import org.apache.isis.applib.value.LocalResourcePath;
import org.apache.isis.extensions.h2console.dom.webmodule.WebModuleH2Console;

@DomainService(
        nature = NatureOfService.VIEW,
        objectType = "isisExtH2Console.H2ManagerMenu"
        )
@DomainServiceLayout(
        named = "Prototyping",
        menuBar = DomainServiceLayout.MenuBar.SECONDARY
        )
public class H2ManagerMenu {

    private final ServiceRegistry serviceRegistry;
    private final WebModuleH2Console webModule;

    @Inject
    public H2ManagerMenu(final ServiceRegistry serviceRegistry, final WebModuleH2Console webModule) {
        this.serviceRegistry = serviceRegistry;
        this.webModule = webModule;
    }


    public static class ActionDomainEvent extends IsisApplibModule.ActionDomainEvent<H2ManagerMenu>{ 
        private static final long serialVersionUID = 1L; }

    @Action(
            semantics = SemanticsOf.SAFE,
            restrictTo = RestrictTo.PROTOTYPING,
            domainEvent = ActionDomainEvent.class
            )
    @ActionLayout(
            named = "H2 Console",
            cssClassFa = "database"
            )
    @MemberOrder(sequence = "500.800")
    public LocalResourcePath openH2Console() {
        if(webModule==null) {
            return null;
        }
        return webModule.getLocalResourcePathIfEnabled();
    }

    public boolean hideOpenH2Console() {
        return webModule==null || webModule.getLocalResourcePathIfEnabled()==null;
    }

}