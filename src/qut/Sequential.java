package qut;

import jaligner.*;
import jaligner.matrix.*;
import edu.au.jacobi.pattern.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Sequential
{
    final static int threads = Runtime.getRuntime().availableProcessors();
    final static ExecutorService executor = Executors.newFixedThreadPool(threads);
    static List<Callable<Object>> callables = new ArrayList<>();

    static HashMap<String, Sigma70Consensus> consensus = new HashMap<>();
    private static Series sigma70_pattern = Sigma70Definition.getSeriesAll_Unanchored(0.7);
    private static final Matrix BLOSUM_62 = BLOSUM62.Load();
    private static byte[] complement = new byte['z'];

    static
    {
        complement['C'] = 'G'; complement['c'] = 'g';
        complement['G'] = 'C'; complement['g'] = 'c';
        complement['T'] = 'A'; complement['t'] = 'a';
        complement['A'] = 'T'; complement['a'] = 't';
    }

                    
    private static List<Gene> ParseReferenceGenes(String referenceFile) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(referenceFile)));
        List<Gene> referenceGenes = new ArrayList<Gene>();
        while (true)
        {
            String name = reader.readLine();
            if (name == null)
                break;
            String sequence = reader.readLine();
            referenceGenes.add(new Gene(name, 0, 0, sequence));
            consensus.put(name, new Sigma70Consensus());
        }
        consensus.put("all", new Sigma70Consensus());
        reader.close();
        return referenceGenes;
    }

    static boolean Homologous(PeptideSequence A, PeptideSequence B)
    {
        return SmithWatermanGotoh.align(new Sequence(A.toString()), new Sequence(B.toString()), BLOSUM_62, 10f, 0.5f).calculateScore() >= 60;
    }

    static NucleotideSequence GetUpstreamRegion(NucleotideSequence dna, Gene gene)
    {
        int upStreamDistance = 250;
        if (gene.location < upStreamDistance)
           upStreamDistance = gene.location-1;

        if (gene.strand == 1)
            return new NucleotideSequence(java.util.Arrays.copyOfRange(dna.bytes, gene.location-upStreamDistance-1, gene.location-1));
        else
        {
            byte[] result = new byte[upStreamDistance];
            int reverseStart = dna.bytes.length - gene.location + upStreamDistance;
            for (int i=0; i<upStreamDistance; i++)
                result[i] = complement[dna.bytes[reverseStart-i]];
            return new NucleotideSequence(result);
        }
    }

    static Match PredictPromoter(NucleotideSequence upStreamRegion)
    {
        return BioPatterns.getBestMatch(sigma70_pattern, upStreamRegion.toString());
    }

    private static void ProcessDir(List<String> list, File dir)
    {
        if (dir.exists())
            for (File file : dir.listFiles())
                if (file.isDirectory())
                    ProcessDir(list, file);
                else
                    list.add(file.getPath());
    }

    private static List<String> ListGenbankFiles(String dir)
    {
        List<String> list = new ArrayList<String>();
        ProcessDir(list, new File(dir));
        return list;
    }

    static GenbankRecord Parse(String file) throws IOException {
        GenbankRecord record = new GenbankRecord();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        record.Parse(reader);
        reader.close();
        return record;
    }

    public static void run(String referenceFile, String dir) throws IOException, InterruptedException {
        List<Gene> referenceGenes = ParseReferenceGenes(referenceFile);

        for (String filename : ListGenbankFiles(dir))
        {
            System.out.println(filename);

            GenbankRecord record = Parse(filename);

            for (Gene referenceGene : referenceGenes)
            {
                System.out.println(referenceGene.name);

                for (Gene gene : record.genes) {
                    callables.add(new Predictor(record, referenceGene, gene));
                }
            }
        }

        executor.invokeAll(callables);

        executor.shutdown();

        for (Map.Entry<String, Sigma70Consensus> entry : consensus.entrySet())
           System.out.println(entry.getKey() + " " + entry.getValue());
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        run("referenceGenes.list", "Ecoli");
    }
}
