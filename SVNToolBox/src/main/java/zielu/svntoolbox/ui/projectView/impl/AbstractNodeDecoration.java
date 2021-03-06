/* 
 * $Id$
 */
package zielu.svntoolbox.ui.projectView.impl;

import java.awt.Color;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor.ColoredFragment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;
import zielu.svntoolbox.FileStatusCalculator;
import zielu.svntoolbox.SvnToolBoxBundle;
import zielu.svntoolbox.SvnToolBoxProject;
import zielu.svntoolbox.async.AsyncFileStatusCalculator;
import zielu.svntoolbox.config.SvnToolBoxAppState;
import zielu.svntoolbox.projectView.ProjectViewManager;
import zielu.svntoolbox.projectView.ProjectViewStatus;
import zielu.svntoolbox.projectView.ProjectViewStatusCache;
import zielu.svntoolbox.projectView.ProjectViewStatusCache.PutResult;
import zielu.svntoolbox.ui.projectView.NodeDecoration;
import zielu.svntoolbox.ui.projectView.NodeDecorationType;
import zielu.svntoolbox.util.LogStopwatch;

/**
 * <p></p>
 * <br/>
 * <p>Created on 12.10.13</p>
 *
 * @author Lukasz Zielinski
 */
public abstract class AbstractNodeDecoration implements NodeDecoration {
    private final static String PREFIX = SvnToolBoxBundle.getString("status.svn.prefix");
    private final static JBColor TEMPORARY_COLOR = new JBColor(new Color(77, 81, 84), new Color(115, 119, 122));
    
    protected final Logger LOG = Logger.getInstance(getClass());    
    protected final FileStatusCalculator myStatusCalc = new FileStatusCalculator();

    protected abstract VirtualFile getVirtualFile(ProjectViewNode node);

    protected JBColor getBranchColor(boolean temporary) {
        if (temporary) {
            return TEMPORARY_COLOR;
        } else {
            return SvnToolBoxAppState.getInstance().getProjectViewDecorationColor();
        }
    }

    private SimpleTextAttributes getBranchAttributes(ProjectViewStatus status) {        
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, getBranchColor(status.isTemporary()));
    }
    
    @Nullable
    protected Object getParentValue(ProjectViewNode node) {
        return node.getParent() != null ? node.getParent().getValue() : null;
    }

    @Nullable
    protected ProjectViewStatus getBranchStatusAndCache(ProjectViewNode node) {
        ProjectViewStatusCache cache = ProjectViewManager.getInstance(node.getProject()).getStatusCache();
        VirtualFile vFile = getVirtualFile(node);
        ProjectViewStatus cached = cache.get(vFile);
        if (cached != null) {
            if (!cached.isEmpty()) {
                return cached;
            }
            return null;
        } else {
            PutResult result = cache.add(vFile, ProjectViewStatus.PENDING);                        
            AsyncFileStatusCalculator.getInstance(node.getProject()).scheduleStatusFor(node.getProject(), vFile);
            if (result != null) {
                return result.getFinalStatus();        
            }
            return null;
        }        
    }

    protected ColoredFragment formatBranchName(ProjectViewStatus status) {
        return new ColoredFragment(" ["+PREFIX+" " + status.getBranchName() + "]", getBranchAttributes(status));
    }

    protected boolean isUnderSvn(ProjectViewNode node, Supplier<Integer> PV_SEQ) {
        LogStopwatch watch = LogStopwatch.debugStopwatch(LOG, PV_SEQ, Suppliers.ofInstance("Under SVN")).start();
        VirtualFile vFile = getVirtualFile(node);
        watch.tick("Get VFile");
        boolean result = false;
        if (vFile != null) {
            boolean underControl = myStatusCalc.fastAllFilesUnderSvn(node.getProject(), vFile);
            watch.tick("Under control={0}", underControl);
            result = underControl;
        }
        watch.stop();
        return result;
    }

    protected boolean shouldApplyDecoration(ProjectViewStatus status) {
        return status != null && status.getBranchName() != null;
    }
    
    protected abstract void applyDecorationUnderSvn(ProjectViewNode node, PresentationData data);
    
    @Override
    public final void decorate(ProjectViewNode node, PresentationData data) {
        Supplier<Integer> PV_SEQ = SvnToolBoxProject.getInstance(node.getProject()).sequence();
        if (isUnderSvn(node, PV_SEQ)) {
            LogStopwatch watch = LogStopwatch.debugStopwatch(LOG, PV_SEQ, Suppliers.ofInstance("Decorate")).start();
            applyDecorationUnderSvn(node, data);
            watch.stop();
        }
    }

    @Override
    public NodeDecorationType getType() {
        return NodeDecorationType.Other;
    }
}
