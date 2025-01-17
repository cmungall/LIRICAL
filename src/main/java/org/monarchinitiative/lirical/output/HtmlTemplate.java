package org.monarchinitiative.lirical.output;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.monarchinitiative.lirical.analysis.Gene2Genotype;
import org.monarchinitiative.lirical.hpo.HpoCase;
import org.monarchinitiative.lirical.likelihoodratio.TestResult;
import org.monarchinitiative.lirical.svg.Lr2Svg;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class coordinates getting the data from the analysis into the FreeMark org.monarchinitiative.lirical.output templates.
 * @author <a href="mailto:peter.robinson@jax.org">Peter Robinson</a>
 */
public class HtmlTemplate extends LiricalTemplate {
    private static final Logger logger = LoggerFactory.getLogger(HtmlTemplate.class);
    /** Threshold posterior probability to show a differential diagnosis in detail. */
    private final double THRESHOLD;
    /** Have the HTML output show at least this many differntials (default: 5). */
    private final int MIN_DIAGNOSES_TO_SHOW;



    /**
     * Constructor to initialize the data that will be needed to output an HTML page.
     * @param hcase The individual (case) represented in the VCF file
     * @param ontology The HPO ontology
     * @param genotypeMap A map of genotypes for all genes with variants in the VCF file
     * @param geneid2sym A map from the Entrez Gene id to the gene symbol
     * @param metadat Metadata about the analysis.
     * @param thres threshold posterior probability to show differential in detail
     */
    public HtmlTemplate(HpoCase hcase,
                        Ontology ontology,
                        Map<TermId, Gene2Genotype> genotypeMap,
                        Map<TermId,String> geneid2sym,
                        Map<String,String> metadat,
                        double thres,
                        int minDifferentials,
                        String prefix,
                        String outdir,
                        List<String> errs){
        super(hcase, ontology, genotypeMap, geneid2sym, metadat);
        initpath(prefix,outdir);
        this.THRESHOLD=thres;
        this.MIN_DIAGNOSES_TO_SHOW=minDifferentials;
        this.templateData.put("errorlist",errs);
        List<DifferentialDiagnosis> diff = new ArrayList<>();
        List<ImprobableDifferential> improbdiff = new ArrayList<>();
        this.topDiagnosisMap=new HashMap<>();
        this.topDiagnosisAnchors=new ArrayList<>();
        ClassLoader classLoader = HtmlTemplate.class.getClassLoader();
        cfg.setClassLoaderForTemplateLoading(classLoader,"");
        templateData.put("postprobthreshold",String.format("%.1f%%",100*THRESHOLD));
        int counter=0;
        for (TestResult result : hcase.getResults()) {
            String symbol=EMPTY_STRING;
            if (result.getPosttestProbability() > THRESHOLD || counter < MIN_DIAGNOSES_TO_SHOW) {
                DifferentialDiagnosis ddx = new DifferentialDiagnosis(result);
                logger.trace("Diff diag for " + result.getDiseaseName());
                if (result.hasGenotype()) {
                    TermId geneId = result.getEntrezGeneId();
                    Gene2Genotype g2g = genotypeMap.get(geneId);
                    if (g2g != null) {
                        symbol = g2g.getSymbol();
                        ddx.addG2G(g2g);
                    } else {
                        ddx.setGenotypeExplanation("no variants found in " + this.geneId2symbol.get(geneId));
                        symbol="no variants found in " + this.geneId2symbol.get(geneId);// will be used by SVG
                    }
                    String expl=result.getGenotypeExplanation();
                    ddx.setGenotypeExplanation(expl);
                } else {
                    ddx.setGenotypeExplanation("No known disease gene");
                }
                ddx.setPhenotypeExplanation(result.getPhenotypeExplanation());
                // now get SVG
                Lr2Svg lr2svg = new Lr2Svg(hcase, result.getDiseaseCurie(), result.getDiseaseName(), ontology, symbol);
                String svg = lr2svg.getSvgString();
                ddx.setSvg(svg);
                diff.add(ddx);
                counter++;
                String counterString=String.format("diagnosis%d",counter);
                this.topDiagnosisAnchors.add(counterString);
                ddx.setAnchor(counterString);
                this.topDiagnosisMap.put(counterString,ddx.getDiseaseName());
            } else {
                if (result.hasGenotype()) {
                    TermId geneId = result.getEntrezGeneId();
                    if (genotypeMap.containsKey(geneId)) {
                        symbol=genotypeMap.get(geneId).getSymbol();
                        int c = genotypeMap.get(geneId).getVarList().size();
                        String name = shortName(result.getDiseaseName());
                        String id = result.getDiseaseCurie().getId();// This is intended to work with OMIM
                        if (name==null) {
                            logger.error("Got null string for disease name from result="+result.toString());
                            name=EMPTY_STRING;// avoid errors
                        }
                        ImprobableDifferential ipd = new ImprobableDifferential(name,id,symbol,result.getPosttestProbability(),c);
                        improbdiff.add(ipd);
                    }
                }
            }
        }
        this.templateData.put("improbdiff",improbdiff);
        this.templateData.put("diff",diff);
    }

    private void initpath(String prefix,String outdir){
        this.outpath=String.format("%s.html",prefix);
        if (outdir != null) {
            File dir = mkdirIfNotExist(outdir);
            this.outpath = Paths.get(dir.getAbsolutePath(),this.outpath).toString();
        }
    }


    /**
     * Constructor to initialize the data that will be needed to output an HTML page.
     * Used for when we have no genetic data
     * @param hcase The individual (case) represented in the VCF file
     * @param ontology The HPO ontology

     * @param metadat Metadata about the analysis.
     * @param thres threshold posterior probability to show differential in detail
     */
    public HtmlTemplate(HpoCase hcase,
                        Ontology ontology,
                        Map<String,String> metadat,
                        double thres,
                        int minDifferentials,
                        String prefix,
                        String outdir,
                        List<String> errs){
        super(hcase, ontology, metadat);
        initpath(prefix,outdir);
        this.THRESHOLD=thres;
        this.MIN_DIAGNOSES_TO_SHOW=minDifferentials;
        this.templateData.put("errorlist",errs);
        List<DifferentialDiagnosis> diff = new ArrayList<>();
        List<ImprobableDifferential> improbdiff = new ArrayList<>();
        this.topDiagnosisMap=new HashMap<>();
        this.topDiagnosisAnchors=new ArrayList<>();
        ClassLoader classLoader = HtmlTemplate.class.getClassLoader();
        cfg.setClassLoaderForTemplateLoading(classLoader,"");
        templateData.put("postprobthreshold",String.format("%.1f%%",100*THRESHOLD));
        int counter=0;
        for (TestResult result : hcase.getResults()) {
            String symbol=EMPTY_STRING;
            if (result.getPosttestProbability() > THRESHOLD || counter < MIN_DIAGNOSES_TO_SHOW) {
                DifferentialDiagnosis ddx = new DifferentialDiagnosis(result);
                logger.trace("Diff diag for " + result.getDiseaseName());
                ddx.setGenotypeExplanation("Genetic data not available");
                // now get SVG
                Lr2Svg lr2svg = new Lr2Svg(hcase, result.getDiseaseCurie(), result.getDiseaseName(), ontology, symbol);
                String svg = lr2svg.getSvgString();
                ddx.setSvg(svg);
                diff.add(ddx);
                counter++;
                String counterString=String.format("diagnosis%d",counter);
                this.topDiagnosisAnchors.add(counterString);
                ddx.setAnchor(counterString);
                ddx.setPhenotypeExplanation(result.getPhenotypeExplanation());
                this.topDiagnosisMap.put(counterString,ddx.getDiseaseName());
            } else {
                TermId geneId = result.getEntrezGeneId();
                String name = shortName(result.getDiseaseName());
                String id = result.getDiseaseCurie().getId();// This is intended to work with OMIM
                if (name==null) {
                    logger.error("Got null string for disease name from result="+result.toString());
                    name=EMPTY_STRING;// avoid errors
                }
                int c=0;
                ImprobableDifferential ipd = new ImprobableDifferential(name,id,symbol,result.getPosttestProbability(),c);
                improbdiff.add(ipd);
            }
        }
        this.templateData.put("improbdiff",improbdiff);
        this.templateData.put("diff",diff);

    }


    @Override
    public void outputFile() {
        logger.info("Writing HTML file to {}",this.outpath);
        try (BufferedWriter out = new BufferedWriter(new FileWriter(this.outpath))) {
            Template template = cfg.getTemplate("liricalHTML.ftl");
            template.process(templateData, out);
        } catch (TemplateException | IOException te) {
            te.printStackTrace();
        }
    }

    @Override
    public void outputFile(String fname) {
        logger.info("Writing HTML file to {}",fname);
        try (BufferedWriter out = new BufferedWriter(new FileWriter(fname))) {
            Template template = cfg.getTemplate("liricalTSV.html");
            template.process(templateData, out);
        } catch (TemplateException | IOException te) {
            te.printStackTrace();
        }
    }





}
