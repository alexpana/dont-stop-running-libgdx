package org.vertexarmy.dsr.level_editor.polygon_editor.actions;

import com.badlogic.gdx.math.Vector2;
import com.beust.jcommander.internal.Lists;
import org.vertexarmy.dsr.core.ActionManager;
import org.vertexarmy.dsr.level_editor.polygon_editor.PolygonEditor;
import org.vertexarmy.dsr.level_editor.polygon_editor.VertexHandler;

import java.util.List;

/**
 * Created by alex
 * on 24.03.2015.
 */
public class AlignHandlersHorizontallyAction extends ActionManager.ActionAdapter {
    private final PolygonEditor polygonEditor;

    private final List<VertexHandler> selectedVertexHandlers = Lists.newArrayList();

    private final List<Vector2> originalVertexPositions = Lists.newArrayList();

    public AlignHandlersHorizontallyAction(PolygonEditor editor, List<VertexHandler> selectedVertexHandlers) {
        polygonEditor = editor;
        this.selectedVertexHandlers.addAll(selectedVertexHandlers);

        for (VertexHandler handler : selectedVertexHandlers) {
            originalVertexPositions.add(editor.getVertex(handler));
        }
    }

    @Override
    public void doAction() {
        float medianY = 0;
        List<VertexHandler> selectedHandlers = polygonEditor.getSelectedHandlers();
        if (selectedHandlers.size() > 0) {
            for (VertexHandler handler : selectedHandlers) {
                medianY += polygonEditor.getVertex(handler).y;
            }

            medianY /= selectedHandlers.size();
            for (VertexHandler handler : selectedHandlers) {
                polygonEditor.getPolygon().getVertices()[handler.vertexIndex * 2 + 1] = medianY;
            }
        }
    }

    @Override
    public void undoAction() {
        for (int i = 0; i < selectedVertexHandlers.size(); ++i) {
            polygonEditor.setVertex(selectedVertexHandlers.get(i), originalVertexPositions.get(i));
        }
    }

    @Override
    public boolean isValid() {
        if (selectedVertexHandlers.size() < 2) {
            return false;
        }

        for (VertexHandler handler : selectedVertexHandlers) {
            if (polygonEditor.getVertex(handler).y != polygonEditor.getVertex(selectedVertexHandlers.get(0)).y) {
                return true;
            }
        }

        return false;
    }
}
