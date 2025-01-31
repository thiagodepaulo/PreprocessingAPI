/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.itera.structures;

import com.itera.preprocess.config.PreProcessingConfig;
import com.itera.util.Tools;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import static com.itera.util.Tools.join;
import java.util.Iterator;
import weka.core.DenseInstance;
import weka.core.SparseInstance;

/**
 *
 * @author root
 */
public class Conversor {

    public static TextData listInputPatternToTextData(List<InputPattern> lInput, PreProcessingConfig config) {
        HashMap<String, Integer> classesIds = new HashMap<>();
        HashMap<Integer, Integer> classesDocs = new HashMap<>();
        HashMap<Integer, Double> termDf = new HashMap<>();
        HashMap<String, Integer> wordIds = new HashMap<>();
        HashMap<Integer, HashMap<Integer, Double>> allDocTermFreq = new HashMap<>();
        int nDocs = lInput.size();
        for (InputPattern input : lInput) {
            if (!input.getClasse().equals(InputPattern.UNLABELED)) {
                if (!classesIds.containsKey(input.getClasse())) {
                    classesIds.put(input.getClasse(), classesIds.size());
                }
                classesDocs.put(input.getId(), classesIds.get(input.getClasse()));
            }
            String[] words = input.getTexto().trim().split("\\s+");
            HashMap<Integer, Double> docTermf = new HashMap<>();
            for (String word : words) {
                if (word.length() <= config.getWordLenghtMin()) {
                    continue;
                }
                // freq by document
                int wid = -1;
                if (wordIds.containsKey(word)) {
                    wid = wordIds.get(word);
                } else {
                    wid = wordIds.size();
                    wordIds.put(word, wid);
                }
                if (docTermf.containsKey(wid)) {
                    docTermf.put(wid, docTermf.get(wid) + 1.);
                } else {
                    docTermf.put(wid, 1.);
                }
                // freq for all doc collection
                if (termDf.containsKey(wid)) {
                    termDf.put(wid, termDf.get(wid) + 1.);
                } else {
                    termDf.put(wid, 1.);
                }
            }
            allDocTermFreq.put(input.getId(), docTermf);
        }
        // calc TF-IDF
        if (config.isTfidf()) {
            for (int docId : allDocTermFreq.keySet()) {
                for (int wordId : allDocTermFreq.get(docId).keySet()) {
                    double freq = allDocTermFreq.get(docId).get(wordId);
                    freq = freq * (1 + (Math.log10((double) nDocs / (1 + termDf.get(wordId)))));
                    allDocTermFreq.get(docId).put(wordId, freq);
                }
            }
        }

        TextData data = new TextData();
        String[] classes = new String[classesIds.size()];
        for (Map.Entry<String, Integer> entryClasses : classesIds.entrySet()) {
            classes[entryClasses.getValue()] = entryClasses.getKey();
        }
        data.setClasses(new ArrayList<>(Arrays.asList(classes)));
        data.setClassesDocuments(classesDocs);
        HashMap<String, Integer> docsIds = new HashMap<>();
        ArrayList<IndexValue>[] documents = new ArrayList[nDocs];
        for (int docId : allDocTermFreq.keySet()) {
            docsIds.put("" + docId, docId);
            ArrayList<IndexValue> docAdjList = new ArrayList<>();
            for (int wordId : allDocTermFreq.get(docId).keySet()) {
                docAdjList.add(new IndexValue(wordId, allDocTermFreq.get(docId).get(wordId)));
            }
            documents[docId] = docAdjList;
        }
        data.setDocsIDs(docsIds);
        data.setDocuments(new ArrayList<>(Arrays.asList(documents)));
        data.setIDsDocs(Tools.invertHashMap(docsIds));
        data.setIDsTerms(Tools.invertHashMap(wordIds));
        data.setTermsIDs(wordIds);
        data.setMapTerms_CompleteTerms(null);

        return data;
    }

    public static Instances dataToArff(Data data) {
        int numEx = data.numExamples();
        int numFeat = data.numFeatures();

        //creating attributes
        ArrayList<Attribute> attrs = new ArrayList<>();
        Feature feat;
        for (int i = 0; i < numFeat; i++) {
            feat = data.getFeature(i);
            if (feat.getType() == Feature.FeatureType.NOMINAL) {
                attrs.add(new Attribute(feat.getFeatureName(), Arrays.asList(feat.categories)));
            } else if (feat.getType() == Feature.FeatureType.NUMERIC) {
                attrs.add(new Attribute(feat.getFeatureName()));
            }
        }
        //creating instances        
        Instances instances = new Instances(data.getDataName(), attrs, numEx);
        instances.setClassIndex(data.getClassIndex());
        attrs = null;

        int r = 0;
        Iterator<? extends Example> itr = data.itrExamples();
        while (itr.hasNext()) {
            //System.out.println(r++); 
            Example ex = itr.next();
            DenseInstance inst = new DenseInstance(numFeat);
            inst.setDataset(instances);
            for (int i = 0; i < numFeat; i++) {
                feat = data.getFeature(i);
                if (feat.getType() == Feature.FeatureType.NOMINAL) {
                    String value = (String) ex.getValue(i);
                    inst.setValue(i, value);
                } else if (feat.getType() == Feature.FeatureType.NUMERIC) {
                    Double value = (Double) ex.getValue(i);
                    inst.setValue(i, value);
                }
            }
            instances.add(inst);
        }

        return instances;
    }

    public static TextData arffToData(String arffArqName) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(arffArqName));
            Instances arff = new Instances(reader);
            reader.close();

            if (arff.classIndex() < 0) {
                arff.setClassIndex(arff.numAttributes() - 1);
            }

            int numAttr = arff.numAttributes();
            int numCls = arff.numClasses();
            int numInsts = arff.numInstances();
            int clsIdx = arff.classIndex();

            TextData data = new TextData();
            ArrayList<String> classes = new ArrayList<>(numCls);
            for (int i = 0; i < numCls; i++) {
                classes.add(i, null);
            }
            HashMap<Integer, Integer> classesDocuments = new HashMap<>();
            HashMap<String, Integer> docsIDs = new HashMap<>();
            ArrayList<ArrayList<IndexValue>> documents = new ArrayList<>();
            HashMap<String, Integer> terms_ids = new HashMap<>();

            for (int i = 0; i < numAttr; i++) {
                if (i != arff.classIndex()) {
                    terms_ids.put(arff.attribute(i).name(), i);
                }
            }
            for (int idx = 0; idx < numInsts; idx++) {
                Instance inst = arff.get(idx);
                int clsPos = (int) (inst.classValue());
                String classStr = inst.toString(clsIdx);
                if (!classes.contains(classStr)) {
                    classes.set(clsPos, classStr);
                }
                ArrayList<IndexValue> doc = new ArrayList<>();
                docsIDs.put("" + idx, idx);
                classesDocuments.put(idx, clsPos);
                for (int attrIdx = 0; attrIdx < numAttr; attrIdx++) {
                    if (attrIdx != arff.classIndex()) {
                        double val = inst.value(attrIdx);
                        if (val > 0.0001) {
                            IndexValue iv = new IndexValue(attrIdx, val);
                            doc.add(iv);
                        }
                    }
                }
                documents.add(doc);
            }

            data.setClasses(classes);
            data.setClassesDocuments(classesDocuments);
            data.setDocsIDs(docsIDs);
            data.setIDsDocs(Tools.invertHashMap(docsIDs));
            data.setDocuments(documents);
            data.setTermsIDs(terms_ids);
            data.setIDsTerms(Tools.invertHashMap(terms_ids));

            return data;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Conversor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Conversor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static void main(String[] args) {
        String s = "     oi, como  vai  você? aêó-fala      ";
        for (String ss : s.trim().split("\\s+")) {
            System.out.println(ss.length());
            System.out.println(ss);
        }
    }

    public static Instances textDataToArff2(TextData data) {
        Instances inst = null;
        try {
            inst = new Instances(new StringReader(textDataToStrArff(data)));
            inst.setClassIndex(inst.numAttributes() - 1);
        } catch (IOException ex) {
            Logger.getLogger(Conversor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return inst;
    }

    public static Instances textDataToArff(TextData data) throws IOException {
        int numDocs = data.getNumDocs();
        int numTerms = data.getNumTerms();

        //creating attributes
        ArrayList<Attribute> attrs = new ArrayList<>();
        for (String word : data.getTerms()) {
            attrs.add(new Attribute(word));
        }
        attrs.add(new Attribute("class_attr", data.getClasses()));
        //creating instances
        Instances instances = new Instances("Itera_Data", attrs, numDocs);
        instances.setClassIndex(numTerms);
        int docId = 0;
        double[] zeros = new double[numTerms + 1];
        for (ArrayList<IndexValue> l : data.getAdjListDocs()) {
            SparseInstance inst = new SparseInstance(numTerms + 1);
            for (IndexValue iv : l) {
                inst.setValue(attrs.get(iv.getIndex()), iv.getValue());
            }
            // set class value
            inst.setValue(attrs.get(numTerms), data.getClassDocument(docId++));
            inst.replaceMissingValues(zeros);
            instances.add(inst);
        }

        return instances;
    }

    public static String textDataToStrArff(TextData data) {
        StringBuilder sb = new StringBuilder();
        String nl = "\n", blank = " ", open = "{", close = "}", comma = ", ";
        String attr = "@ATTRIBUTE", real = "REAL";
        int classIdx = data.getNumTerms();

        sb.append("@RELATION IteraDATA");
        sb.append(nl);
        sb.append(nl);
        for (int wid = 0; wid < data.getTerms().size(); wid++) {
            sb.append(attr);
            sb.append(blank);
            sb.append(data.getTermName(wid));
            sb.append(blank);
            sb.append(real);
            sb.append(nl);
        }
        sb.append("@ATTRIBUTE class_  {" + join(", ", data.getClasses()) + "}");
        sb.append(nl);
        sb.append(nl);
        sb.append(nl);
        sb.append("@DATA\n");

        for (int docId : data.getDocsIds()) {

            int classId = data.getClassDocument(docId);
            sb.append(open);

            // Sorting by index
            Collections.sort(data.getAdjListDoc(docId), new Comparator<IndexValue>() {
                @Override
                public int compare(IndexValue iv1, IndexValue iv2) {
                    return Integer.compare(iv1.getIndex(), iv2.getIndex());
                }
            });
            for (IndexValue iv : data.getAdjListDoc(docId)) {
                sb.append(iv.getIndex());
                sb.append(blank);
                sb.append(String.format(Locale.US, "%.4f", iv.getValue()));
                sb.append(comma);
            }
            sb.append(classIdx);
            sb.append(blank);
            sb.append(data.getClasses().get(classId));
            sb.append(close);
            sb.append(nl);
        }
        return sb.toString();
    }

}
