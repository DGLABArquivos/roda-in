package org.roda.rodain.creation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.roda.rodain.core.I18n;
import org.roda.rodain.creation.ui.CreationModalProcessing;
import org.roda.rodain.rules.TreeNode;
import org.roda.rodain.schema.DescriptionObject;
import org.roda.rodain.sip.SipPreview;
import org.roda.rodain.sip.SipRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.loc.repository.bagit.Bag;
import gov.loc.repository.bagit.BagFactory;
import gov.loc.repository.bagit.PreBag;
import gov.loc.repository.bagit.writer.impl.ZipWriter;

/**
 * @author Andre Pereira apereira@keep.pt
 * @since 19/11/2015.
 */
public class BagitSipCreator extends SimpleSipCreator {
  private static final Logger LOGGER = LoggerFactory.getLogger(BagitSipCreator.class.getName());
  private static final String DATAFOLDER = "data";

  private Instant startTime;

  /**
   * Creates a new BagIt exporter.
   *
   * @param outputPath
   *          The path to the output folder of the SIP exportation
   * @param previews
   *          The map with the SIPs that will be exported
   */
  public BagitSipCreator(Path outputPath, Map<DescriptionObject, List<String>> previews) {
    super(outputPath, previews);

    for (DescriptionObject obj : previews.keySet()) {
      if(obj instanceof SipPreview) {
        SipPreview sip = (SipPreview) obj;
        for (SipRepresentation sr : sip.getRepresentations()) {
          for (TreeNode tn : sr.getFiles()) {
            try {
              allSipsSize += nodeSize(tn);
            } catch (IOException e) {
              LOGGER.error("Can't access file: " + tn.getPath(), e);
            }
          }
        }
      }
    }
  }

  /**
   * Attempts to create a BagIt SIP of each SipPreview
   */
  @Override
  public void run() {
    startTime = Instant.now();
    for (DescriptionObject preview : previews.keySet()) {
      if (canceled) {
        break;
      }
      createBagit(preview);
    }
    currentAction = I18n.t("done");
  }

  private void createBagit(DescriptionObject descriptionObject) {
    // we add a timestamp to the beginning of the SIP name to avoid same name
    // conflicts
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd kk'h'mm'm'ss's'SSS");
    String dateToString = format.format(new Date());
    String timestampedName = String.format("%s %s.zip", dateToString, descriptionObject.getTitle());
    currentSipName = descriptionObject.getTitle();
    currentAction = actionCreatingFolders;
    // make the directories
    Path name = outputPath.resolve(timestampedName);
    Path data = name.resolve(DATAFOLDER);
    new File(data.toString()).mkdirs();

    try {
      if(descriptionObject instanceof SipPreview) {
        SipPreview sip = (SipPreview) descriptionObject;
        for (SipRepresentation sr : sip.getRepresentations()) {
          Set<TreeNode> files = sr.getFiles();
          currentAction = actionCopyingData;
          for (TreeNode tn : files)
            createFiles(tn, data);
        }
      }

      BagFactory bf = new BagFactory();
      PreBag pb = bf.createPreBag(new File(name.toString()));
      Bag b = pb.makeBagInPlace(BagFactory.Version.V0_97, false);

      // additional metadata
      b.getBagInfoTxt().put("id", descriptionObject.getId());
      b.getBagInfoTxt().put("parent", descriptionObject.getParentId());
      b.getBagInfoTxt().put("title", descriptionObject.getTitle());
      b.getBagInfoTxt().put("level", "item");

      currentAction = actionCopyingMetadata;
      Map<String, String> metadataList = descriptionObject.getMetadataWithReplaces();
      if (!metadataList.isEmpty()) {
        metadataList.forEach((id, content) -> b.getBagInfoTxt().put("metadata." + id, content));
      }

      b.makeComplete();

      currentAction = actionFinalizingSip;
      ZipWriter zipWriter = new ZipWriter(bf);
      zipWriter.write(b, new File(name.toString()));
      zipWriter.endPayload();
      createdSipsCount++;
      b.close();
    } catch (Exception e) {
      LOGGER.error("Error creating SIP", e);
      unsuccessful.add(descriptionObject);
      CreationModalProcessing.showError(descriptionObject, e);
      deleteDirectory(name);
    }
  }

  /**
   * Estimates the remaining time needed to finish exporting the SIPs.
   *
   * <p>
   * The estimate time is the sum of the data copy time and the other processes
   * time. To estimate the data copy time, we first find the average copy speed
   * and then divide the remaining data size by that speed.
   * </p>
   *
   * <p>
   * The other processes (metadata copy and finalizing) are estimated together,
   * and can be obtained by subtracting the data copy time from the elapsed
   * time. By dividing that result by the number of already exported SIPs, we
   * get the average time these processes took.
   * </p>
   *
   * @return The estimate in milliseconds
   */
  @Override
  public double getTimeRemainingEstimate() {
    // prevent divide by zero
    if (transferedTime == 0)
      return -1;
    // estimate the remaining data copy time for the current SIP
    float allSpeed = transferedSize / transferedTime;
    long allSizeLeft = allSipsSize - transferedSize;
    long sizeLeft = sipSize - sipTransferedSize;
    float sipRemaining = sizeLeft / allSpeed;

    // 80% is the progress of the data copy of current SIP
    // the other 20% are for the SIP finalization
    // divide the result by the number of SIPs because this should be the
    // progress of 1 SIP
    currentSipProgress = (sipTransferedSize / (float) sipSize) * 0.8f;
    currentSipProgress /= sipPreviewCount;

    // estimate the time remaining for the other SIPs, except the data copy time
    long timeSinceStart = Duration.between(startTime, Instant.now()).toMillis();
    long allOtherTime = timeSinceStart - transferedTime;
    int createdSips = getCreatedSipsCount();
    float eachOtherTime;
    if (createdSips != 0) {
      eachOtherTime = allOtherTime / createdSips;
    } else { // if the finishing time is very small, set it to 70% of the
      // estimated time
      eachOtherTime = (sipSize / allSpeed) * 0.7f;
    }

    // time = data copy estimate + other SIP's estimate (without copy time)
    int remaining = sipPreviewCount - createdSips;
    float dataTime = sipRemaining + (allSizeLeft / allSpeed);
    long sipTime = Duration.between(sipStartInstant, Instant.now()).toMillis();
    float sipOtherTime = sipTime - sipTransferedTime;
    float otherTime = (eachOtherTime * remaining) - sipOtherTime;

    return dataTime + otherTime;
  }

  private long nodeSize(TreeNode node) throws IOException {
    Path nodePath = node.getPath();
    long result = 0;
    if (Files.isDirectory(nodePath)) {
      for (TreeNode tn : node.getChildren().values()) {
        result += nodeSize(tn);
      }
    } else {
      result += Files.size(nodePath);
    }
    return result;
  }

  private void createFiles(TreeNode node, Path dest) throws IOException {
    sipSize = nodeSize(node);
    sipTransferedSize = 0;
    sipTransferedTime = 0;
    sipStartInstant = Instant.now();
    recCreateFiles(node, dest);
  }

  private void recCreateFiles(TreeNode node, Path dest) throws IOException {
    Path nodePath = node.getPath();
    if (Files.isDirectory(nodePath)) {
      Path directory = dest.resolve(nodePath.getFileName().toString());
      new File(directory.toString()).mkdir();
      for (TreeNode tn : node.getChildren().values()) {
        recCreateFiles(tn, directory);
      }
    } else {
      Path destination = dest.resolve(nodePath.getFileName().toString());
      copyFile(nodePath, destination);
    }
  }

  private void copyFile(Path path, Path dest) {
    final int progressCheckpoint = 1000;
    long bytesCopied = 0, previousLen = 0;
    File destFile = dest.toFile();

    try {
      long totalBytes = Files.size(path);
      InputStream in = new FileInputStream(path.toFile());
      OutputStream out = new FileOutputStream(destFile);
      byte[] buf = new byte[1024];
      int counter = 0;
      int len;
      lastInstant = Instant.now();

      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
        counter += len;
        bytesCopied += (destFile.length() - previousLen);
        previousLen = destFile.length();
        if (counter > progressCheckpoint || bytesCopied == totalBytes) {
          sipTransferedSize += counter;
          transferedSize += counter;
          Instant now = Instant.now();
          Duration dur = Duration.between(lastInstant, now);
          transferedTime += dur.toMillis();
          sipTransferedTime += dur.toMillis();
          lastInstant = now;
          counter = 0;
        }
      }
      in.close();
      out.close();
    } catch (IOException e) {
      LOGGER.error("Error writing(copying) file. Source: " + path + "; Destination: " + dest, e);
    }
  }
}
