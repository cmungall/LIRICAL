package org.monarchinitiative.lr2pg.io;

import com.github.phenomics.ontolib.formats.hpo.HpoFrequency;
import com.github.phenomics.ontolib.formats.hpo.HpoTerm;
import com.github.phenomics.ontolib.ontology.data.TermId;
import com.github.phenomics.ontolib.formats.hpo.HpoTermRelation;
import com.github.phenomics.ontolib.ontology.data.*;
import com.google.common.collect.ImmutableList;
import org.apache.log4j.Logger;
import org.monarchinitiative.lr2pg.hpo.HpoDiseaseWithMetadata;
import org.monarchinitiative.lr2pg.hpo.HpoOnset;
import org.monarchinitiative.lr2pg.hpo.TermIdWithMetadata;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class parses the phenotype_annotation.tab file into a collection of HpoDisease objects.
 */
public class HpoAnnotation2DiseaseParser {
    static Logger logger = Logger.getLogger(HpoAnnotation2DiseaseParser.class.getName());

    private String annotationFilePath =null;
    private Ontology<HpoTerm, HpoTermRelation> hpoPhenotypeOntology=null;
    private Ontology<HpoTerm, HpoTermRelation> inheritancePhenotypeOntology=null;

    private static final TermPrefix HP_PREFIX = new ImmutableTermPrefix("HP");
    private static final TermId ONSET_TID = new ImmutableTermId(HP_PREFIX,"0003674");


    Map<String,HpoDiseaseWithMetadata> diseaseMap;




    public HpoAnnotation2DiseaseParser(String annotationFile, Ontology<HpoTerm, HpoTermRelation> phenotypeOntology,
                                       Ontology<HpoTerm, HpoTermRelation> inheritanceOntology){
        this.annotationFilePath =annotationFile;
        this.hpoPhenotypeOntology=phenotypeOntology;
        this.inheritancePhenotypeOntology=inheritanceOntology;
        this.diseaseMap=new HashMap<>();
        parseAnnotation();
    }


    public Map<String, HpoDiseaseWithMetadata> getDiseaseMap() {
        return diseaseMap;
    }

    /**
     * @return A list of disease-HPO phenotype annotations.
     */
    public void  parseAnnotation() {
        logger.trace(String.format("Parsing annotations at %s",annotationFilePath));
        // First stage of parsing is to get the lines parsed and sorted acccording to disease.
        Map<String,List<AnnotationLine>> disease2AnnotLineMap = new HashMap<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(this.annotationFilePath));
            String line=null;
            while ((line=br.readLine())!=null) {
                System.out.println(line);
                AnnotationLine aline = parseAnnotationLine(line);
                List annots=null;
                if (disease2AnnotLineMap.containsKey(aline.DBObjectId)) {
                    annots = disease2AnnotLineMap.get(aline.DBObjectId);
                } else {
                    annots = new ArrayList();
                    disease2AnnotLineMap.put(aline.DBObjectId,annots);
                }

                annots.add(aline);
            }

            br.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    // When we get down here, we have added all of the disease annotations to the disease2AnnotLineMap
        // Now we want to transform that into HpoDisease objects
        for (String diseaseId : disease2AnnotLineMap.keySet()) {
            List<AnnotationLine> annots=disease2AnnotLineMap.get(diseaseId);
            final ImmutableList.Builder<TermIdWithMetadata> phenoListBuilder = ImmutableList.builder();
            final ImmutableList.Builder<TermId> inheritanceListBuilder = ImmutableList.builder();
            final ImmutableList.Builder<TermId> negativeTermListBuilder = ImmutableList.builder();
            String diseaseName=null;
            for (AnnotationLine line: annots) {
                if (isInheritanceTerm( line.hpoId) ) {
                    inheritanceListBuilder.add(line.hpoId);
                } else if (line.NOT) {
                    negativeTermListBuilder.add(line.hpoId);
                } else {
                    TermIdWithMetadata tidm = new TermIdWithMetadata(line.hpoId,line.freqeuncyModifier,line.onsetModifier);
                    phenoListBuilder.add(tidm);
                }
                if (line.DbObjectName!=null) diseaseName=line.DbObjectName;
            }
            HpoDiseaseWithMetadata hpoDisease = new HpoDiseaseWithMetadata(diseaseName,
                    diseaseId,
                    phenoListBuilder.build(),
                    inheritanceListBuilder.build(),
                    negativeTermListBuilder.build());
           this.diseaseMap.put(hpoDisease.getDiseaseDatabaseId(),hpoDisease);
        }
    }



   private boolean isInheritanceTerm(TermId tid) {
       return inheritancePhenotypeOntology.getAncestorTermIds(tid) != null &&
               inheritancePhenotypeOntology.getAncestorTermIds(tid).contains(ONSET_TID);
   }






    /**
     * Go from HP:0000123 to the corresponding TermId
     * @param hp
     * @return
     */
    private TermId string2TermId(String hp) {
        if (! hp.startsWith("HP:"))
            return null;
        else
            return new ImmutableTermId(HP_PREFIX,hp.substring(3));
    }



    private HpoFrequency getFrequency(String freq) {
        if (freq==null || freq.isEmpty()) return null;
        try {
            TermId tid = string2TermId(freq);
            return HpoFrequency.fromTermId(tid);
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private HpoOnset getOnset(String ons) {
        if (ons==null || ons.isEmpty()) return null;
        return HpoOnset.fromHpoIdString(ons);
    }



    public AnnotationLine parseAnnotationLine(String line) {
        String A[]=line.split("\t");
        if (A.length != 14) {
            logger.error(String.format("Malformed annotation line with %d (instead of 14) fields: %s",A.length,line ));
            return null;
        }
        String DB=A[0];
        String DBObjectId=A[1];
        String DbObjectName=A[2];
        String NOT=A[3];
        TermId hpoId=string2TermId(A[4]);
        HpoOnset onsetModifier=getOnset(A[7]);
        HpoFrequency freqeuncyModifier= getFrequency(A[8]);
        boolean no=false;
        if (NOT!=null && NOT.equals("NOT")) no = true;
        AnnotationLine aline = new AnnotationLine(DB,DBObjectId,DbObjectName,no, hpoId,onsetModifier,freqeuncyModifier);
        return aline;
    }

    /**
     * A convenience class that will allow us to collect the annotation lines for each disease that we want to
     * parse; from these data, we will construct the {@link org.monarchinitiative.lr2pg.hpo.HpoDiseaseWithMetadata}
     * objects
     */
    public static class AnnotationLine {
        String database;
        String DBObjectId;
        String DbObjectName;
        boolean NOT=false;
        TermId hpoId;
        HpoOnset onsetModifier;
        HpoFrequency freqeuncyModifier;

        public AnnotationLine(String DB, String objectId,String name,boolean isNot, TermId termId, HpoOnset onset, HpoFrequency freq) {
            database=DB;
            DBObjectId=objectId;
            DbObjectName=name;
            NOT=isNot;
            termId=termId;
            onsetModifier=onset;
            freqeuncyModifier=freq;
        }


    }




}
