/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ml.knn;

import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.ml.TestUtils;
import org.apache.ignite.ml.common.TrainerTest;
import org.apache.ignite.ml.dataset.feature.extractor.Vectorizer;
import org.apache.ignite.ml.dataset.feature.extractor.impl.DoubleArrayVectorizer;
import org.apache.ignite.ml.knn.ann.ANNClassificationModel;
import org.apache.ignite.ml.knn.ann.ANNClassificationTrainer;
import org.apache.ignite.ml.knn.classification.NNStrategy;
import org.apache.ignite.ml.math.distances.EuclideanDistance;
import org.junit.Assert;
import org.junit.Test;

/** Tests behaviour of ANNClassificationTest. */
public class ANNClassificationTest extends TrainerTest {
    /** */
    @Test
    public void testBinaryClassification() {
        Map<Integer, double[]> cacheMock = new HashMap<>();

        for (int i = 0; i < twoClusters.length; i++)
            cacheMock.put(i, twoClusters[i]);

        ANNClassificationTrainer trainer = new ANNClassificationTrainer()
            .withK(10)
            .withMaxIterations(10)
            .withEpsilon(1e-4)
            .withDistance(new EuclideanDistance());

        Assert.assertEquals(10, trainer.getK());
        Assert.assertEquals(10, trainer.getMaxIterations());
        TestUtils.assertEquals(1e-4, trainer.getEpsilon(), PRECISION);
        Assert.assertEquals(new EuclideanDistance(), trainer.getDistance());

        NNClassificationModel mdl = trainer.fit(
            cacheMock,
            parts,
            new DoubleArrayVectorizer<Integer>().labeled(Vectorizer.LabelCoordinate.FIRST)
        ).withK(3)
            .withDistanceMeasure(new EuclideanDistance())
            .withStrategy(NNStrategy.SIMPLE);

        Assert.assertNotNull(((ANNClassificationModel) mdl).getCandidates());

        Assert.assertTrue(mdl.toString().contains(NNStrategy.SIMPLE.name()));
        Assert.assertTrue(mdl.toString(true).contains(NNStrategy.SIMPLE.name()));
        Assert.assertTrue(mdl.toString(false).contains(NNStrategy.SIMPLE.name()));
    }

    /** */
    @Test
    public void testUpdate() {
        Map<Integer, double[]> cacheMock = new HashMap<>();

        for (int i = 0; i < twoClusters.length; i++)
            cacheMock.put(i, twoClusters[i]);

        ANNClassificationTrainer trainer = new ANNClassificationTrainer()
            .withK(10)
            .withMaxIterations(10)
            .withEpsilon(1e-4)
            .withDistance(new EuclideanDistance());

        ANNClassificationModel originalMdl = (ANNClassificationModel) trainer.fit(
            cacheMock,
            parts,
            new DoubleArrayVectorizer<Integer>().labeled(Vectorizer.LabelCoordinate.FIRST)
        ).withK(3)
            .withDistanceMeasure(new EuclideanDistance())
            .withStrategy(NNStrategy.SIMPLE);

        ANNClassificationModel updatedOnSameDataset = (ANNClassificationModel) trainer.update(originalMdl,
            cacheMock, parts,
            new DoubleArrayVectorizer<Integer>().labeled(Vectorizer.LabelCoordinate.LAST)
        ).withK(3)
            .withDistanceMeasure(new EuclideanDistance())
            .withStrategy(NNStrategy.SIMPLE);

        ANNClassificationModel updatedOnEmptyDataset = (ANNClassificationModel) trainer.update(originalMdl,
            new HashMap<Integer, double[]>(), parts,
            new DoubleArrayVectorizer<Integer>().labeled(Vectorizer.LabelCoordinate.LAST)
        ).withK(3)
            .withDistanceMeasure(new EuclideanDistance())
            .withStrategy(NNStrategy.SIMPLE);

        Assert.assertNotNull(updatedOnSameDataset.getCandidates());

        Assert.assertTrue(updatedOnSameDataset.toString().contains(NNStrategy.SIMPLE.name()));
        Assert.assertTrue(updatedOnSameDataset.toString(true).contains(NNStrategy.SIMPLE.name()));
        Assert.assertTrue(updatedOnSameDataset.toString(false).contains(NNStrategy.SIMPLE.name()));

        Assert.assertNotNull(updatedOnEmptyDataset.getCandidates());

        Assert.assertTrue(updatedOnEmptyDataset.toString().contains(NNStrategy.SIMPLE.name()));
        Assert.assertTrue(updatedOnEmptyDataset.toString(true).contains(NNStrategy.SIMPLE.name()));
        Assert.assertTrue(updatedOnEmptyDataset.toString(false).contains(NNStrategy.SIMPLE.name()));
    }
}
