/*-
 *
 *  * Copyright 2016 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */
package org.deeplearning4j.arbiter.task;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.deeplearning4j.arbiter.GraphConfiguration;
import org.deeplearning4j.arbiter.listener.DL4JArbiterStatusReportingListener;
import org.deeplearning4j.arbiter.optimize.api.Candidate;
import org.deeplearning4j.arbiter.optimize.api.OptimizationResult;
import org.deeplearning4j.arbiter.optimize.api.TaskCreator;
import org.deeplearning4j.arbiter.optimize.api.data.DataProvider;
import org.deeplearning4j.arbiter.optimize.api.evaluation.ModelEvaluator;
import org.deeplearning4j.arbiter.optimize.api.score.ScoreFunction;
import org.deeplearning4j.arbiter.optimize.runner.CandidateInfo;
import org.deeplearning4j.arbiter.optimize.runner.CandidateStatus;
import org.deeplearning4j.arbiter.optimize.runner.listener.StatusListener;
import org.deeplearning4j.arbiter.scoring.util.ScoreUtil;
import org.deeplearning4j.earlystopping.EarlyStoppingConfiguration;
import org.deeplearning4j.earlystopping.EarlyStoppingResult;
import org.deeplearning4j.earlystopping.trainer.EarlyStoppingGraphTrainer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Task creator for ComputationGraph
 *
 * @param <A> Additional evaluation type
 * @author Alex Black
 */
@AllArgsConstructor
@NoArgsConstructor
public class ComputationGraphTaskCreator<A> implements TaskCreator<GraphConfiguration, ComputationGraph, Object, A> {

    private ModelEvaluator<ComputationGraph, Object, A> modelEvaluator;

    @Override
    public Callable<OptimizationResult<GraphConfiguration, ComputationGraph, A>> create(
                    Candidate<GraphConfiguration> candidate, DataProvider<Object> dataProvider,
                    ScoreFunction<ComputationGraph, Object> scoreFunction, List<StatusListener> statusListener) {

        return new GraphLearningTask<>(candidate, dataProvider, scoreFunction, modelEvaluator, statusListener);
    }


    private static class GraphLearningTask<A>
                    implements Callable<OptimizationResult<GraphConfiguration, ComputationGraph, A>> {

        private Candidate<GraphConfiguration> candidate;
        private DataProvider<Object> dataProvider;
        private ScoreFunction<ComputationGraph, Object> scoreFunction;
        private ModelEvaluator<ComputationGraph, Object, A> modelEvaluator;
        private List<StatusListener> listeners;

        public GraphLearningTask(Candidate<GraphConfiguration> candidate, DataProvider<Object> dataProvider,
                        ScoreFunction<ComputationGraph, Object> scoreFunction,
                        ModelEvaluator<ComputationGraph, Object, A> modelEvaluator, List<StatusListener> listeners) {
            this.candidate = candidate;
            this.dataProvider = dataProvider;
            this.scoreFunction = scoreFunction;
            this.modelEvaluator = modelEvaluator;
            this.listeners = listeners;
        }


        @Override
        public OptimizationResult<GraphConfiguration, ComputationGraph, A> call() throws Exception {
            CandidateInfo ci = new CandidateInfo(candidate.getIndex(), CandidateStatus.Running, null,
                    System.currentTimeMillis(), null, null, candidate.getFlatParameters(), null);

            //Create network
            ComputationGraph net = new ComputationGraph(candidate.getValue().getConfiguration());
            net.init();

            if(listeners != null){
                net.setListeners(new DL4JArbiterStatusReportingListener(listeners, ci));
            }

            //Early stopping or fixed number of epochs:
            DataSetIterator dataSetIterator = ScoreUtil.getIterator(dataProvider.trainData(candidate.getDataParameters()));


            EarlyStoppingConfiguration<ComputationGraph> esConfig = candidate.getValue().getEarlyStoppingConfiguration();
            EarlyStoppingResult<ComputationGraph> esResult = null;
            if (esConfig != null) {
                EarlyStoppingGraphTrainer trainer = new EarlyStoppingGraphTrainer(esConfig, net, dataSetIterator, null); //dl4jListener);
                esResult = trainer.fit();
                net = esResult.getBestModel(); //Can return null if failed OR if

                switch (esResult.getTerminationReason()) {
                    case Error:
                        ci.setCandidateStatus(CandidateStatus.Failed);
                        ci.setExceptionStackTrace(esResult.getTerminationDetails());
                        break;
                    case IterationTerminationCondition:
                    case EpochTerminationCondition:
                        ci.setCandidateStatus(CandidateStatus.Complete);
                        break;
                }

            } else {
                //Fixed number of epochs
                int nEpochs = candidate.getValue().getNumEpochs();
                for (int i = 0; i < nEpochs; i++) {
                    net.fit(dataSetIterator);
                }
                ci.setCandidateStatus(CandidateStatus.Complete);
            }

            A additionalEvaluation = null;
            if (esConfig != null && esResult.getTerminationReason() != EarlyStoppingResult.TerminationReason.Error) {
                additionalEvaluation = (modelEvaluator != null ? modelEvaluator.evaluateModel(net, dataProvider) : null);
            }

            Double score = null;
            if (net != null) {
                score = scoreFunction.score(net, dataProvider, candidate.getDataParameters());
                ci.setScore(score);
            }

            return new OptimizationResult<>(candidate, net, score, candidate.getIndex(), additionalEvaluation, ci);
        }
    }
}
