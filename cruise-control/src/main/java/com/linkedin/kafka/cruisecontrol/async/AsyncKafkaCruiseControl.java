/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License"). See License in the project root for license information.
 */

package com.linkedin.kafka.cruisecontrol.async;

import com.codahale.metrics.MetricRegistry;
import com.linkedin.kafka.cruisecontrol.KafkaCruiseControl;
import com.linkedin.kafka.cruisecontrol.analyzer.GoalOptimizer;
import com.linkedin.kafka.cruisecontrol.async.progress.OperationProgress;
import com.linkedin.kafka.cruisecontrol.async.progress.Pending;
import com.linkedin.kafka.cruisecontrol.common.KafkaCruiseControlThreadFactory;
import com.linkedin.kafka.cruisecontrol.config.KafkaCruiseControlConfig;
import com.linkedin.kafka.cruisecontrol.model.ClusterModel;
import com.linkedin.kafka.cruisecontrol.monitor.ModelCompletenessRequirements;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * A KafkaCruiseControl extension with asynchronous operations support.
 * 
 * The following async methods are supported:
 * 
 * <ul>
 * <li>{@link KafkaCruiseControl#decommissionBrokers(Collection, boolean, boolean, List, ModelCompletenessRequirements, OperationProgress)}</li>
 * <li>{@link KafkaCruiseControl#addBrokers(Collection, boolean, boolean, List, ModelCompletenessRequirements, OperationProgress)}</li>
 * <li>{@link KafkaCruiseControl#clusterModel(long, ModelCompletenessRequirements, OperationProgress)}</li>
 * <li>{@link KafkaCruiseControl#clusterModel(long, long, ModelCompletenessRequirements, OperationProgress)}</li>
 * <li>{@link KafkaCruiseControl#getOptimizationProposals(OperationProgress)}</li>
 * <li>{@link KafkaCruiseControl#getOptimizationProposals(List, ModelCompletenessRequirements, OperationProgress)}</li>
 * <li>{@link KafkaCruiseControl#rebalance(List, boolean, ModelCompletenessRequirements, OperationProgress)}</li>
 * </ul>
 * 
 * The other operations are non-blocking by default.
 */
public class AsyncKafkaCruiseControl extends KafkaCruiseControl {
  private final ExecutorService _sessionExecutor = 
      Executors.newFixedThreadPool(3, new KafkaCruiseControlThreadFactory("ServletSessionExecutor", 
                                                                          true, 
                                                                          null));

  /**
   * Construct the Cruise Control
   * 
   * @param config the configuration of Cruise Control.
   * @param dropwizardMetricRegistry the metric registry for metrics.              
   */
  public AsyncKafkaCruiseControl(KafkaCruiseControlConfig config, MetricRegistry dropwizardMetricRegistry) {
    super(config, dropwizardMetricRegistry);
  }

  /**
   * @see KafkaCruiseControl#decommissionBrokers(Collection, boolean, boolean, List, ModelCompletenessRequirements, 
   * OperationProgress) 
   */
  public OperationFuture<GoalOptimizer.OptimizerResult> decommissionBrokers(Collection<Integer> brokerIds,
                                                                            boolean dryRun,
                                                                            boolean throttleDecommissionedBrokers,
                                                                            List<String> goals,
                                                                            ModelCompletenessRequirements requirements) {
    OperationFuture<GoalOptimizer.OptimizerResult> future = new OperationFuture<>("Decommission brokers");
    pending(future.operationProgress());
    _sessionExecutor.submit(new DecommissionBrokersRunnable(this, future, brokerIds, dryRun,
                                                            throttleDecommissionedBrokers, goals, requirements));
    return future;
  }

  /**
   * @see KafkaCruiseControl#addBrokers(Collection, boolean, boolean, List, ModelCompletenessRequirements, 
   * OperationProgress) 
   */
  public OperationFuture<GoalOptimizer.OptimizerResult> addBrokers(Collection<Integer> brokerIds,
                                                                   boolean dryRun,
                                                                   boolean throttleAddedBrokers,
                                                                   List<String> goals,
                                                                   ModelCompletenessRequirements requirements) {
    OperationFuture<GoalOptimizer.OptimizerResult> future = new OperationFuture<>("Add brokers");
    pending(future.operationProgress());
    _sessionExecutor.submit(new AddBrokerRunnable(this, future, brokerIds, dryRun, throttleAddedBrokers,
                                                  goals, requirements));
    return future;
  }

  /**
   * @see KafkaCruiseControl#clusterModel(long, ModelCompletenessRequirements, OperationProgress) 
   */
  public OperationFuture<ClusterModel> clusterModel(long time, ModelCompletenessRequirements requirements) {
    OperationFuture<ClusterModel> future = new OperationFuture<>("Get cluster model");
    pending(future.operationProgress());
    _sessionExecutor.submit(new GetClusterModelUntilRunnable(this, future, time, requirements));
    return future;
  }

  /**
   * @see KafkaCruiseControl#clusterModel(long, long, ModelCompletenessRequirements, OperationProgress) 
   */
  public OperationFuture<ClusterModel> clusterModel(long startMs,
                                                    long endMs,
                                                    ModelCompletenessRequirements requirements) {
    OperationFuture<ClusterModel> future = 
        new OperationFuture<>(String.format("Get cluster model from %d to %d", startMs, endMs));
    pending(future.operationProgress());
    _sessionExecutor.submit(new GetClusterModelInRangeRunnable(this, future, startMs, endMs, requirements));
    return future;
  }

  /**
   * Get the {@link com.linkedin.kafka.cruisecontrol.model.ClusterModel.BrokerStats} for the cluster model.
   * 
   * @see KafkaCruiseControl#clusterModel(long, ModelCompletenessRequirements, OperationProgress) 
   */
  public OperationFuture<ClusterModel.BrokerStats> getBrokerStats(long time, 
                                                                  ModelCompletenessRequirements requirements) {
    OperationFuture<ClusterModel.BrokerStats> future = new OperationFuture<>("Get broker stats");
    pending(future.operationProgress());
    _sessionExecutor.submit(new GetBrokerStatsRunnable(this, future, time, requirements));
    return future;
  }

  /**
   * @see KafkaCruiseControl#getOptimizationProposals(OperationProgress) 
   */
  public OperationFuture<GoalOptimizer.OptimizerResult> getOptimizationProposals() {
    OperationFuture<GoalOptimizer.OptimizerResult> future = 
        new OperationFuture<>("Get default optimization proposals");
    pending(future.operationProgress());
    _sessionExecutor.submit(new GetOptimizationProposalsRunnable(this, future, null, null));
    return future;
  }

  /**
   * @see KafkaCruiseControl#getOptimizationProposals(List, ModelCompletenessRequirements, OperationProgress)  
   */
  public OperationFuture<GoalOptimizer.OptimizerResult> getOptimizationProposals(List<String> goals,
                                                                                 ModelCompletenessRequirements requirements) {
    OperationFuture<GoalOptimizer.OptimizerResult> future = 
        new OperationFuture<>("Get customized optimization proposals");
    pending(future.operationProgress());
    _sessionExecutor.submit(new GetOptimizationProposalsRunnable(this, future, goals, requirements));
    return future;
  }

  /**
   * @see KafkaCruiseControl#rebalance(List, boolean, ModelCompletenessRequirements, OperationProgress) 
   */
  public OperationFuture<GoalOptimizer.OptimizerResult> rebalance(List<String> goals,
                                                                  boolean dryRun,
                                                                  ModelCompletenessRequirements requirements) {
    OperationFuture<GoalOptimizer.OptimizerResult> future = new OperationFuture<>("Rebalance");
    pending(future.operationProgress());
    _sessionExecutor.submit(new RebalanceRunnable(this, future, goals, dryRun, requirements));
    return future;
  }
  
  private void pending(OperationProgress progress) {
    progress.addStep(new Pending());
  }
}
