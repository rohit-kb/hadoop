/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.security;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.apache.hadoop.security.RuleBasedLdapGroupsMapping
    .CONVERSION_RULE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;

/**
 * Test cases to verify the rules supported by RuleBasedLdapGroupsMapping.
 */
public class TestRuleBasedLdapGroupsMapping  {

  @Test
  public void testGetGroupsToUpper() throws NamingException {
    RuleBasedLdapGroupsMapping groupsMapping = spy(
        new RuleBasedLdapGroupsMapping());
    Set<String> groups = new LinkedHashSet<>();
    groups.add("group1");
    groups.add("group2");
    doReturn(groups).when((LdapGroupsMapping) groupsMapping)
        .doGetGroups(eq("admin"), anyInt());

    Configuration conf = new Configuration();
    conf.set(LdapGroupsMapping.LDAP_URL_KEY, "ldap://test");
    conf.set(CONVERSION_RULE_KEY, "to_upper");
    groupsMapping.setConf(conf);

    List<String> groupsUpper = new ArrayList<>();
    groupsUpper.add("GROUP1");
    groupsUpper.add("GROUP2");
    assertEquals(groupsUpper, groupsMapping.getGroups("admin"));
  }

  @Test
  public void testGetGroupsToLower() throws NamingException {
    RuleBasedLdapGroupsMapping groupsMapping = spy(
        new RuleBasedLdapGroupsMapping());
    Set<String> groups = new LinkedHashSet<>();
    groups.add("GROUP1");
    groups.add("GROUP2");
    doReturn(groups).when((LdapGroupsMapping) groupsMapping)
        .doGetGroups(eq("admin"), anyInt());

    Configuration conf = new Configuration();
    conf.set(LdapGroupsMapping.LDAP_URL_KEY, "ldap://test");
    conf.set(CONVERSION_RULE_KEY, "to_lower");
    groupsMapping.setConf(conf);

    List<String> groupsLower = new ArrayList<>();
    groupsLower.add("group1");
    groupsLower.add("group2");
    assertEquals(groupsLower, groupsMapping.getGroups("admin"));
  }

  @Test
  public void testGetGroupsInvalidRule() throws NamingException {
    RuleBasedLdapGroupsMapping groupsMapping = spy(
        new RuleBasedLdapGroupsMapping());
    Set<String> groups = new LinkedHashSet<>();
    groups.add("group1");
    groups.add("GROUP2");
    doReturn(groups).when((LdapGroupsMapping) groupsMapping)
        .doGetGroups(eq("admin"), anyInt());

    Configuration conf = new Configuration();
    conf.set(LdapGroupsMapping.LDAP_URL_KEY, "ldap://test");
    conf.set(CONVERSION_RULE_KEY, "none");
    groupsMapping.setConf(conf);

    assertEquals(groups, groupsMapping.getGroupsSet("admin"));
  }

}
