package qut;

import java.util.*;
import java.util.concurrent.Callable;
import edu.au.jacobi.pattern.Match;
import static qut.Sequential.*;

public class Predictor implements Callable<Object> {
    private GenbankRecord record;
    private Gene referenceGene;
    private Gene gene;

    public Predictor(GenbankRecord record, Gene referenceGene, Gene gene) {
        this.record = record;
        this.referenceGene = referenceGene;
        this.gene = gene;
    }

    @Override
    public HashMap<Gene, Match> call() {
        if (Homologous(this.gene.sequence, this.referenceGene.sequence)) {
            NucleotideSequence upStreamRegion = GetUpstreamRegion(this.record.nucleotides, this.gene);
            Match prediction = PredictPromoter(upStreamRegion);

            if (prediction != null) {
                consensus.get(this.referenceGene.name).addMatch(prediction);
                consensus.get("all").addMatch(prediction);
            }
        }

        return null;
    }
}
