/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.cluster.placement;

import org.apache.solr.cluster.Cluster;

/**
 * Placement context makes it easier to pass around and access main placement-related components.
 */
public interface PlacementContext {
  /**
   * Initial state of the cluster. Note there are {@link java.util.Set}'s and {@link
   * java.util.Map}'s accessible from the {@link Cluster} and other reachable instances. These
   * collection will not change while the plugin is executing and will be thrown away once the
   * plugin is done. The plugin code can therefore modify them if needed.
   */
  Cluster getCluster();

  /**
   * Factory used by the plugin to fetch additional attributes from the cluster nodes, such as count
   * of cores, system properties etc..
   */
  AttributeFetcher getAttributeFetcher();

  /** Factory used to create instances of {@link PlacementPlan} to return computed decision. */
  PlacementPlanFactory getPlacementPlanFactory();

  /** Factory used to create instances of {@link BalancePlan} to return computed decision. */
  BalancePlanFactory getBalancePlanFactory();
}
