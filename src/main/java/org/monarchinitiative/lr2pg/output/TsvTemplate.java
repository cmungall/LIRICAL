package org.monarchinitiative.lr2pg.output;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monarchinitiative.lr2pg.analysis.Gene2Genotype;
import org.monarchinitiative.lr2pg.hpo.HpoCase;
import org.monarchinitiative.lr2pg.likelihoodratio.TestResult;
import org.monarchinitiative.phenol.formats.hpo.HpoOntology;
import org.monarchinitiative.phenol.ontology.data.TermId;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class coordinates the output of a TSV file that contains a suymmary of the analysis results.
 * @author <a href="mailto:peter.robinson@jax.org">Peter Robinson</a>
 */
public class TsvTemplate extends Lr2pgTemplate {
    private static final Logger logger = LogManager.getLogger();

    private static final String[] tsvHeader={"rank","diseaseName","diseaseCurie","pretestprob","posttestprob",
            "compositeLR","entrezGeneId","variants"};



    public TsvTemplate(HpoCase hcase,
                       HpoOntology ontology,
                       Map<TermId, Gene2Genotype> genotypeMap,
                       Map<TermId, String> geneid2sym,
                       Map<String, String> metadat) {
        super(hcase, ontology, genotypeMap, geneid2sym, metadat);
        ClassLoader classLoader = TsvTemplate.class.getClassLoader();
        cfg.setClassLoaderForTemplateLoading(classLoader,"");
        List<TsvDifferential> diff = new ArrayList<>();
        String header= Arrays.stream(tsvHeader).collect(Collectors.joining("\t"));
        templateData.put("header",header);
        int counter=0;
        // Note the following results are already sorted
        for (TestResult result : hcase.getResults()) {
            String symbol = EMPTY_STRING;
            TsvDifferential tsvdiff = new TsvDifferential(result);
            logger.trace("Diff diag for " + result.getDiseaseName());
            if (result.hasGenotype()) {
                TermId geneId = result.getEntrezGeneId();
                Gene2Genotype g2g = genotypeMap.get(geneId);
                if (g2g != null) {
                    symbol = g2g.getSymbol();
                    tsvdiff.addG2G(g2g);
                } else {
                    tsvdiff.setNoVariantsFoundString("no variants found in " + this.geneId2symbol.get(geneId));
                    symbol = "no variants found in " + this.geneId2symbol.get(geneId);// will be used by SVG (IntelliJ warning wrong)
                }
            } else {
                tsvdiff.setNoVariantsFoundString("No known disease gene");
            }
            diff.add(tsvdiff);
            counter++;
        }
        this.templateData.put("diff",diff);
    }


    @Override
    public void outputFile(){
        logger.debug("Outputting TSV file");
        try (BufferedWriter out = new BufferedWriter(new FileWriter("myout.tsv"))) {
            Template template = cfg.getTemplate("lr2pgTSV.ftl");
            template.process(templateData, out);
        } catch (TemplateException | IOException te) {
            te.printStackTrace();
        }
    }
}