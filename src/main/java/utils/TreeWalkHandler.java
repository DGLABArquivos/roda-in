package utils;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Created by adrapereira on 01-10-2015.
 */
public interface TreeWalkHandler {
    void preVisitDirectory(Path path);
    void postVisitDirectory(Path path);
    void visitFile(Path path, BasicFileAttributes attrs);
    void visitFileFailed(Path path);
    void end();
}
