package org.vertexarmy.dsr.leveleditor.editors.polygon;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.beust.jcommander.internal.Maps;
import org.vertexarmy.dsr.core.component.InputComponent;

import java.util.Map;

/**
 * created by Alex
 * on 3/23/2015.
 */
class PolygonEditorInputComponent implements InputComponent, InputProcessor {
    private final PolygonEditor polygonEditor;

    private final Map<Integer, PolygonEditor.EditModeType> hotkeys = Maps.newHashMap();

    public PolygonEditorInputComponent(final PolygonEditor polygonEditor) {
        this.polygonEditor = polygonEditor;

        hotkeys.put(Input.Keys.NUM_1, PolygonEditor.EditModeType.DEFAULT);
        hotkeys.put(Input.Keys.NUM_2, PolygonEditor.EditModeType.ADD_VERTEX);
    }

    @Override
    public InputProcessor getInputAdapter() {
        return this;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!polygonEditor.isBound()) {
            return false;
        }

        if (hotkeys.containsKey(keycode)) {
            polygonEditor.setEditMode(hotkeys.get(keycode));
            return true;
        }
        return polygonEditor.getEditMode().keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return polygonEditor.isBound() && polygonEditor.getEditMode().keyUp(keycode);

    }

    @Override
    public boolean keyTyped(char character) {
        return polygonEditor.isBound() && polygonEditor.getEditMode().keyTyped(character);

    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return polygonEditor.isBound() && polygonEditor.getEditMode().touchDown(screenX, screenY, pointer, button);

    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return polygonEditor.isBound() && polygonEditor.getEditMode().touchUp(screenX, screenY, pointer, button);

    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return polygonEditor.isBound() && polygonEditor.getEditMode().touchDragged(screenX, screenY, pointer);

    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return polygonEditor.isBound() && polygonEditor.getEditMode().mouseMoved(screenX, screenY);

    }

    @Override
    public boolean scrolled(int amount) {
        return polygonEditor.isBound() && polygonEditor.getEditMode().scrolled(amount);

    }
}
