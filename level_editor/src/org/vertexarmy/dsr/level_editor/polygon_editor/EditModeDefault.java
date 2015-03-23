package org.vertexarmy.dsr.level_editor.polygon_editor;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import lombok.RequiredArgsConstructor;
import org.vertexarmy.dsr.core.DragHelper;
import org.vertexarmy.dsr.core.systems.RenderSystem;

/**
 * created by Alex
 * on 3/23/2015.
 */
@RequiredArgsConstructor
public class EditModeDefault extends InputAdapter implements EditMode {
    private final PolygonEditor polygonEditor;

    private final DragHelper dragHelper = new DragHelper();

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        VertexHandler hoveredHandler = polygonEditor.getHoveredVertexHandler();

        if (hoveredHandler != null) {
            hoveredHandler.setDragged(true);
            dragHelper.beginDrag(mouseWorld(screenX, screenY));
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        VertexHandler hoveredHandler = polygonEditor.getHoveredVertexHandler();

        if (hoveredHandler != null) {
            hoveredHandler.setDragged(false);
            dragHelper.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        for (VertexHandler handler : polygonEditor.getVertexHandlers()) {
            float xp = polygonEditor.getPolygon().getVertices()[handler.vertexIndex * 2];
            float yp = polygonEditor.getPolygon().getVertices()[handler.vertexIndex * 2 + 1];
            Vector2 m = mouseWorld(screenX, screenY);

            handler.setHovered(Math.abs(xp - m.x) < handler.hitSize / 2 / RenderSystem.instance().getZoom() &&
                    Math.abs(yp - m.y) < handler.hitSize / 2 / RenderSystem.instance().getZoom());
        }

        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        VertexHandler draggedHandler = polygonEditor.getDraggedVertexHandler();
        if (draggedHandler != null) {
            int i = draggedHandler.vertexIndex;
            Vector2 dragOffset = dragHelper.getDragOffset(mouseWorld(screenX, screenY));
            dragHelper.reset(mouseWorld(screenX, screenY));
            polygonEditor.getPolygon().getVertices()[i * 2] += dragOffset.x;
            polygonEditor.getPolygon().getVertices()[i * 2 + 1] += dragOffset.y;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        dragHelper.endDrag();
    }

    @Override
    public void render() {
    }

    private Vector2 mouseWorld(int screenX, int screenY) {
        return RenderSystem.instance().screenToWorld(new Vector2(screenX, screenY));
    }
}