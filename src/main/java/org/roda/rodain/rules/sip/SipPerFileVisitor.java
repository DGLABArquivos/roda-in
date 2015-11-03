package org.roda.rodain.rules.sip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import org.apache.commons.io.FilenameUtils;
import org.roda.rodain.rules.MetadataTypes;
import org.roda.rodain.rules.TreeNode;
import org.roda.rodain.rules.filters.ContentFilter;
import org.roda.rodain.utils.TreeVisitor;

/**
 * @author Andre Pereira apereira@keep.pt
 * @since 05-10-2015.
 */
public class SipPerFileVisitor extends Observable implements TreeVisitor, SipCreator {
    private static final int UPDATEFREQUENCY = 500; //in milliseconds
    private static final String METADATA_EXT = ".xml";
    private ArrayList<SipPreview> sips;
    private int added = 0, returned = 0;
    private long lastUIUpdate = 0;

    private String id;
    private Set<ContentFilter> filters;
    private MetadataTypes metaType;
    private String metadataContent;
    private Path metadataPath;

    public SipPerFileVisitor(String id, Set<ContentFilter> filters, MetadataTypes metaType, Path metadataPath, String metadataContent){
        sips = new ArrayList<>();
        this.id = id;
        this.filters = filters;
        this.metaType = metaType;
        this.metadataPath = metadataPath;
        this.metadataContent = metadataContent;
    }

    @Override
    public List<SipPreview> getSips() {
        return sips;
    }
    @Override
    public int getCount(){
        return added;
    }
    @Override
    public SipPreview getNext(){
        return sips.get(returned++);
    }
    @Override
    public boolean hasNext(){
        return returned < added;
    }
    @Override
    public void setStartPath(String st){
    }

    @Override
    public void preVisitDirectory(Path path, BasicFileAttributes attrs) {
    }

    @Override
    public void postVisitDirectory(Path path) {

    }

    private boolean filter(Path path){
        String pathString = path.toString();
        for(ContentFilter cf: filters){
            if(cf.filter(pathString))
                return true;
        }
        return false;
    }

    @Override
    public void visitFile(Path path, BasicFileAttributes attrs) {
        if(filter(path))
            return;

        Path metaPath = getMetadataPath(path);
        TreeNode node = new TreeNode(path);
        Set<TreeNode> files = new HashSet<>();
        files.add(node);

        SipPreview sipPreview = new SipPreview(path.getFileName().toString(), files, metaPath, metadataContent);
        node.addObserver(sipPreview);

        sips.add(sipPreview);
        added ++;

        long now = System.currentTimeMillis();
        if(now - lastUIUpdate > UPDATEFREQUENCY) {
            setChanged();
            notifyObservers();
            lastUIUpdate = now;
        }
    }

    private Path getMetadataPath(Path path){
        Path result;
        switch (metaType){
            case SINGLEFILE:
                result = metadataPath;
                break;
            case DIFFDIRECTORY: // uses the same logic as the next case
            case SAMEDIRECTORY:
                result = getFileFromDir(path);
                break;
            default:
                return null;
        }
        return result;
    }

    private Path getFileFromDir(Path path){
        String fileName = FilenameUtils.removeExtension(path.getFileName().toString());
        Path newPath = metadataPath.resolve(fileName + METADATA_EXT);
        if(Files.exists(newPath)){
            return newPath;
        }
        newPath = metadataPath.resolve(path.getFileName() + METADATA_EXT);
        if(Files.exists(newPath)){
            return newPath;
        }
        return null;
    }

    @Override
    public void visitFileFailed(Path path) {
    }

    @Override
    public void end() {
        setChanged();
        notifyObservers();
    }

    @Override
    public String getId(){
        return id;
    }
}
