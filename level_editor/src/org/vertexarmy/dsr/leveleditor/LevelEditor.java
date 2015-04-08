package org.vertexarmy.dsr.leveleditor;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import org.vertexarmy.dsr.Version;
import org.vertexarmy.dsr.core.ActionManager;
import org.vertexarmy.dsr.core.Log;
import org.vertexarmy.dsr.core.Root;
import org.vertexarmy.dsr.core.Serialization;
import org.vertexarmy.dsr.core.assets.FontRepository;
import org.vertexarmy.dsr.core.assets.TextureRepository;
import org.vertexarmy.dsr.core.component.ComponentType;
import org.vertexarmy.dsr.core.component.InputComponent;
import org.vertexarmy.dsr.core.component.Node;
import org.vertexarmy.dsr.core.component.RenderComponent;
import org.vertexarmy.dsr.core.systems.RenderSystem;
import org.vertexarmy.dsr.game.level.Level;
import org.vertexarmy.dsr.game.level.LevelSprite;
import org.vertexarmy.dsr.game.level.TerrainPatch;
import org.vertexarmy.dsr.graphics.TextureOverlay;
import org.vertexarmy.dsr.leveleditor.cameracontroller.AutoScrollCameraController;
import org.vertexarmy.dsr.leveleditor.cameracontroller.UserPanningCameraController;
import org.vertexarmy.dsr.leveleditor.editors.polygon.PolygonEditor;
import org.vertexarmy.dsr.leveleditor.editors.polygon.PolygonEditorListener;
import org.vertexarmy.dsr.leveleditor.editors.sprite.SpriteEditor;
import org.vertexarmy.dsr.leveleditor.editors.sprite.SpriteEditorListener;
import org.vertexarmy.dsr.leveleditor.levelrenderer.LevelRenderer;
import org.vertexarmy.dsr.leveleditor.ui.*;
import org.vertexarmy.dsr.leveleditor.ui.genericeditor.GenericEditor;
import org.vertexarmy.dsr.math.Polygon;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

class LevelEditor extends Game {
    private final Log log = Log.create();

    private final Root root = new Root();

    private final Function<LevelEditor, Boolean> initTask;

    private final LevelRenderer levelRenderer = new LevelRenderer();

    private final GridRenderer gridRenderer = new GridRenderer();

    private File boundLevelFile;

    private Level level;

    private PolygonEditor terrainPolygonEditor = new PolygonEditor();

    private SpriteEditor spriteEditor;

    private Toolbox toolbox;

    private DebugValuesPanel debugValuesPanel;

    private LevelSaveDialog saveDialog;

    private LevelLoadDialog loadDialog;

    private SpritePickerDialog spritePickerDialog;

    private LevelBackgroundDialog levelBackgroundDialog;

    private UserPanningCameraController userUserPanningCameraController = new UserPanningCameraController();

    private AutoScrollCameraController autoScrollCameraController = new AutoScrollCameraController();

    private GenericEditor terrainPatchTextureOverlayEditor;

    private LevelEditor(Function<LevelEditor, Boolean> initTask) {
        this.initTask = initTask;
    }

    @Override
    public void create() {
        root.initialize();

        loadAssets();

        ElegantGraySkin.install(root.getUiNode().getUiSkin());

        initUI();
        spriteEditor = new SpriteEditor(root);

        root.addNode(new Node(ComponentType.INPUT, userUserPanningCameraController));
        root.addNode(new Node(ComponentType.UPDATE, autoScrollCameraController));
        root.addNode(createEditorNode());
        root.addNode(terrainPolygonEditor.getNode());
        root.addNode(spriteEditor.getNode());

        setLevel(Level.createDefaultLevel());

        userUserPanningCameraController.setEnabled(true);

        terrainPatchTextureOverlayEditor = new GenericEditor(root.getUiNode().getStage(), "Texture Overlay", root.getUiNode().getUiSkin(), TextureOverlay.class);

        terrainPolygonEditor.setListener(new PolygonEditorListener() {
            @Override
            public void deletePolygonRequested() {
                ActionManager.instance().runAction(new RemoveTerrainPatchAction(level, findTerrainPatch(terrainPolygonEditor.getPolygon())));
            }
        });

        spriteEditor.setListener(new SpriteEditorListener() {
            @Override
            public void deleteSpriteRequested() {
                ActionManager.instance().runAction(new RemoveTerrainSpriteAction(level, spriteEditor.getLevelSprite()));
            }
        });

        if (initTask != null) {
            initTask.apply(this);
        }
    }

    private TerrainPatch findTerrainPatch(Polygon patchShape) {
        for (TerrainPatch terrainPatch : level.getTerrainPatches()) {
            if (terrainPatch.getShape() == patchShape) {
                return terrainPatch;
            }
        }
        return null;
    }

    private Node createEditorNode() {
        Node originNode = new Node(ComponentType.RENDER, new RenderComponent() {
            @Override
            public void render() {
                update();

                gridRenderer.renderGrid();

                levelRenderer.render();

                gridRenderer.renderRulers();
            }
        });

        originNode.addComponent(ComponentType.INPUT, new InputComponent() {
            @Override
            public InputProcessor getInputAdapter() {
                return new InputAdapter() {
                    @Override
                    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                        if (terrainPolygonEditor.getHoveredVertex() != null) {
                            return false;
                        }

                        ItemPicker.PickResult pickResult = ItemPicker.pickObject(level, screenX, screenY);

                        if (pickResult.getType() == ItemPicker.ItemType.TERRAIN_POLYGON) {
                            TerrainPatch pickedTerrainPatch = (TerrainPatch) pickResult.getObject();
                            terrainPatchTextureOverlayEditor.bindToObject(pickedTerrainPatch.getTextureOverlay());
                            pickSpriteForEditing(null);
                            return pickTerrainPatchForEditing(pickedTerrainPatch);
                        }

                        if (pickResult.getType() == ItemPicker.ItemType.LEVEL_SPRITE) {
                            pickTerrainPatchForEditing(null);
                            return pickSpriteForEditing((LevelSprite) pickResult.getObject());
                        }

                        return false;
                    }

                    @Override
                    public boolean keyDown(int keycode) {
                        if (Shortcuts.isHideDebugInfoShortcut(keycode)) {
                            debugValuesPanel.setVisible(!debugValuesPanel.isVisible());
                            return true;
                        }

                        if (Shortcuts.isHideUIShortcut(keycode)) {
                            root.getUiNode().getContentTable().setVisible(!root.getUiNode().getContentTable().isVisible());
                            return true;
                        }

                        if (Shortcuts.isSaveShortcut(keycode)) {
                            if (boundLevelFile == null) {
                                saveDialog.show();
                            } else {
                                saveLevel();
                            }
                            return true;
                        }

                        if (Shortcuts.isOpenShortcut(keycode)) {
                            loadDialog.show();
                            return true;
                        }

                        if (Shortcuts.isDeselectShortcut(keycode)) {
                            pickTerrainPatchForEditing(null);
                            pickSpriteForEditing(null);
                            return true;
                        }

                        if (Shortcuts.isFullscreenShortcut(keycode)) {
                            RenderSystem.instance().toggleFullscreen();
                            return true;
                        }

                        if (Shortcuts.isSpritePickerShortcut(keycode)) {
                            spritePickerDialog.show();
                        }

                        if (Shortcuts.isEditBackgroundShortcut(keycode)) {
                            levelBackgroundDialog.show();
                        }

                        return false;
                    }
                };
            }
        });
        return originNode;
    }

    private void loadAssets() {
        loadFonts();
        loadTextures();
    }

    private void loadFonts() {
        FontRepository.instance().loadFont(AssetName.FONT_MARKE_8, Gdx.files.internal("fonts/marke_eigenbau_normal_8.fnt"));
        FontRepository.instance().loadFont(AssetName.FONT_VERA_SANS_MONO_10, Gdx.files.internal("fonts/vera_sans_mono_10.fnt"));
    }

    private void loadTextures() {
        Texture tilesTexture = new Texture(Gdx.files.internal("tiles.png"));
        TextureRepository.instance().addTexture("grass", new TextureRegion(tilesTexture, 0, 0, 32, 32));
        TextureRepository.instance().addTexture("dirt", new TextureRegion(tilesTexture, 32, 0, 32, 32));
        TextureRepository.instance().addTexture("saw", new TextureRegion(tilesTexture, 64, 0, 64, 64));

        TextureRepository.instance().loadTextureAtlas(Gdx.files.internal("ui/ui_icons.atlas"));
        TextureRepository.instance().loadTextureAtlas(Gdx.files.internal("ui/ui_icons_background.atlas"));

        for (File textureFile : FileUtils.discoverFiles(new File("textures"), ImmutableList.of("png", "jpg"))) {
            String textureName = textureFile.getName();
            if (textureName.endsWith(".png") || textureName.endsWith(".jpg")) {
                TextureRepository.instance().addTexture(textureName, new TextureRegion(new Texture(Gdx.files.internal("textures/" + textureName))));
            }
        }
    }

    private void initUI() {
        debugValuesPanel = new DebugValuesPanel(root.getUiNode().getUiSkin());
        debugValuesPanel.setVisible(false);

        toolbox = new Toolbox(root.getUiNode().getUiSkin(), new Toolbox.Listener() {
            @Override
            public void loadFileRequested() {
                loadDialog.show();
            }

            @Override
            public void saveFileRequested() {
                saveDialog.show();
            }

            @Override
            public void autoScrollForwardRequested() {
                userUserPanningCameraController.setEnabled(false);
                autoScrollCameraController.increaseSpeed();
            }

            @Override
            public void pauseAutoScrollRequested() {
                userUserPanningCameraController.setEnabled(true);
                autoScrollCameraController.resetSpeed();
            }

            @Override
            public void autoScrollBackwardRequested() {
                userUserPanningCameraController.setEnabled(false);
                autoScrollCameraController.decreaseSpeed();
            }

            @Override
            public void editTerrainPatch() {
                terrainPatchTextureOverlayEditor.show();
            }
        });

        root.getUiNode().getContentTable().add(toolbox).expandX().fill().row();
        root.getUiNode().getContentTable().add(debugValuesPanel).left().row();

        saveDialog = new LevelSaveDialog(root.getUiNode().getStage(), "Save Level", root.getUiNode().getUiSkin());
        saveDialog.setListener(new Dialog.Listener<LevelSaveDialog.Event>() {
            @Override
            public void dialogAccepted(LevelSaveDialog.Event event) {
                setBoundLevelFile(new File("levels/" + event.getFilename() + ".json"));
                saveLevel();
            }
        });

        loadDialog = new LevelLoadDialog(root.getUiNode().getStage(), "Load Level", root.getUiNode().getUiSkin());
        loadDialog.setListener(new Dialog.Listener<LevelLoadDialog.Event>() {
            @Override
            public void dialogAccepted(LevelLoadDialog.Event event) {
                log.debug("Requested to load the level " + event.getLevelName());

                try {
                    loadLevel(new File("levels/" + event.getLevelName() + ".json"));
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
            }
        });

        spritePickerDialog = new SpritePickerDialog(root.getUiNode().getStage(), "Select terrain texture", root.getUiNode().getUiSkin());

        levelBackgroundDialog = new LevelBackgroundDialog(root.getUiNode().getStage(), "Edit Background", root.getUiNode().getUiSkin());
        levelBackgroundDialog.setListener(new Dialog.Listener<LevelBackgroundDialog.Event>() {
            @Override
            public void dialogAccepted(LevelBackgroundDialog.Event event) {
                level.getBackgroundLayers().clear();
                level.getBackgroundLayers().addAll(event.getBackgroundLayers());
                levelRenderer.reloadLevel();
            }
        });
    }

    public void loadLevel(File selectedFile) {
        try {
            log.debug("Attempting to load file " + selectedFile);
            FileInputStream inputStream = new FileInputStream(selectedFile);

            Level level = Serialization.deserialize(inputStream, Level.class);

            terrainPolygonEditor.unbindFromPolygon();
            spriteEditor.unbindFromSprite();

            setLevel(level);

            setBoundLevelFile(selectedFile);
        } catch (Exception e) {
            log.exception(e);
        }
    }

    private void saveLevel() {
        if (boundLevelFile != null) {
            try {
                FileOutputStream outputStream = new FileOutputStream(boundLevelFile);
                Serialization.serialize(outputStream, level);
                log.info("Saved level " + boundLevelFile.getAbsolutePath());
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        } else {
            log.warning("Attempted to save a level which is not bound to an external file.");
        }
    }

    private void setBoundLevelFile(File selectedFile) {
        boundLevelFile = selectedFile;
        if (boundLevelFile != null) {
            Gdx.graphics.setTitle(boundLevelFile.getAbsolutePath() + " - Level Editor - " + Version.value());
        }
    }

    @Override
    public void render() {
        root.update();
    }

    @Override
    public void resize(int w, int h) {
        root.handleResize(w, h);
    }

    private void update() {
        Camera camera = RenderSystem.instance().getCamera();
        float zoom = RenderSystem.instance().getZoom();

        DebugValues.instance().setValue(DebugItems.CAMERA_POSITION, (int) camera.position.x + ", " + (int) camera.position.y);
        DebugValues.instance().setValue(DebugItems.ZOOM, String.valueOf(zoom * 100) + " %");
        DebugValues.instance().setValue(DebugItems.MOUSE_VIEWPORT, Gdx.input.getX() + ", " + Gdx.input.getY());

        Vector2 mouseWorld = RenderSystem.instance().screenToWorld(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        DebugValues.instance().setValue(DebugItems.MOUSE_WORLD, (int) mouseWorld.x + ", " + (int) mouseWorld.y);

        DebugValues.instance().setValue(DebugItems.FPS, String.valueOf(Gdx.graphics.getFramesPerSecond()));

        levelRenderer.reloadLevel();
    }

    private void setLevel(Level level) {
        this.level = level;

        levelRenderer.setLevel(level);
    }

    private boolean pickTerrainPatchForEditing(@Nullable TerrainPatch terrainPatch) {
        if (terrainPatch == null) {
            terrainPatchTextureOverlayEditor.bindToObject(null);
            return terrainPolygonEditor.unbindFromPolygon();
        } else {
            terrainPatchTextureOverlayEditor.bindToObject(terrainPatch.getTextureOverlay());
            return terrainPolygonEditor.bindToPolygon(terrainPatch.getShape());
        }
    }

    private boolean pickSpriteForEditing(LevelSprite levelSprite) {
        if (levelSprite == null) {
            return spriteEditor.unbindFromSprite();
        } else {
            return spriteEditor.bindToSprite(levelSprite);
        }
    }

    public static void launch(Function<LevelEditor, Boolean> initTask) {
        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.width = 1000;
        config.height = 800;
        config.fullscreen = false;
        config.vSyncEnabled = true;
        config.title = "Level Editor - " + Version.value();
        new LwjglApplication(new LevelEditor(initTask), config);
    }

}
