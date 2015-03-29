package org.vertexarmy.dsr.leveleditor;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PolygonSprite;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Function;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import org.vertexarmy.dsr.Version;
import org.vertexarmy.dsr.core.Log;
import org.vertexarmy.dsr.core.Root;
import org.vertexarmy.dsr.core.Serialization;
import org.vertexarmy.dsr.core.UiNode;
import org.vertexarmy.dsr.core.assets.FontRepository;
import org.vertexarmy.dsr.core.component.ComponentType;
import org.vertexarmy.dsr.core.component.InputComponent;
import org.vertexarmy.dsr.core.component.Node;
import org.vertexarmy.dsr.core.component.RenderComponent;
import org.vertexarmy.dsr.core.systems.RenderSystem;
import org.vertexarmy.dsr.game.Level;
import org.vertexarmy.dsr.game.Tiles;
import org.vertexarmy.dsr.graphics.SpriteFactory;
import org.vertexarmy.dsr.leveleditor.polygoneditor.PolygonEditor;
import org.vertexarmy.dsr.leveleditor.ui.DebugValuesPanel;
import org.vertexarmy.dsr.leveleditor.ui.Dialog;
import org.vertexarmy.dsr.leveleditor.ui.LevelLoadDialog;
import org.vertexarmy.dsr.leveleditor.ui.LevelSaveDialog;
import org.vertexarmy.dsr.leveleditor.ui.Toolbox;

class LevelEditor extends Game {
    private static final SpriteFactory SPRITE_FACTORY = SpriteFactory.getInstance();

    private final Log log = Log.create();

    private final JFileChooser fileChooser = new JFileChooser();

    private final Root root = new Root();

    private final Map<Tiles, TextureRegion> tiles = new HashMap<>();

    private final Function<LevelEditor, Boolean> initTask;

    private File boundLevelFile;

    private Level level;

    private PolygonSprite terrainSprite;

    private PolygonEditor terrainPolygonEditor;

    private UiNode uiNode;

    private Toolbox toolbox;

    private DebugValuesPanel debugValuesPanel;

    private GridRenderer gridRenderer;
    private LevelSaveDialog saveDialog;
    private LevelLoadDialog loadDialog;

    private LevelEditor(Function<LevelEditor, Boolean> initTask) {
        this.initTask = initTask;
    }

    @Override
    public void create() {
        root.initialize();

        FontRepository.instance().loadFont(AssetName.FONT_MARKE_8, Gdx.files.internal("fonts/marke_eigenbau_normal_8.fnt"));
        FontRepository.instance().loadFont(AssetName.FONT_VERA_SANS_MONO_10, Gdx.files.internal("fonts/vera_sans_mono_10.fnt"));

        root.addNode(new Node(ComponentType.INPUT, new CameraController()));

        uiNode = new UiNode();
        root.addNode(uiNode);

        gridRenderer = new GridRenderer();

        Node originNode = new Node(ComponentType.RENDER, new RenderComponent() {
            @Override
            public void render() {
                doUpdate();

                PolygonSpriteBatch polygonSpriteBatch = RenderSystem.instance().getPolygonSpriteBatch();

                gridRenderer.renderGrid();

                if (level != null) {
                    polygonSpriteBatch.begin();
                    terrainSprite.draw(polygonSpriteBatch);
                    polygonSpriteBatch.end();
                }

                DebugValues.instance().setValue(DebugItems.FPS, String.valueOf(Gdx.graphics.getFramesPerSecond()));
            }
        });

        originNode.addComponent(ComponentType.INPUT, new InputComponent() {
            @Override
            public InputProcessor getInputAdapter() {
                return new InputAdapter() {
                    @Override
                    public boolean keyDown(int keycode) {
                        if (keycode == Input.Keys.F1) {
                            debugValuesPanel.setVisible(!debugValuesPanel.isVisible());
                            return true;
                        }

                        if (keycode == Input.Keys.F2) {
                            uiNode.getContentTable().setVisible(!uiNode.getContentTable().isVisible());
                            return true;
                        }

                        if (isSaveShortcut(keycode)) {
                            if (boundLevelFile == null) {
                                saveDialog.show();
                            } else {
                                saveLevel();
                            }

                            return true;
                        }

                        if (isOpenShortcut(keycode)) {
                            openLevelFile();
                            return true;
                        }
                        return false;
                    }

                    private boolean isOpenShortcut(int keycode) {
                        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && keycode == Input.Keys.O;
                    }

                    private boolean isSaveShortcut(int keycode) {
                        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && keycode == Input.Keys.S;
                    }
                };
            }
        });

        root.addNode(originNode);

        Texture tilesTexture = new Texture(Gdx.files.internal("tiles.png"));
        tiles.put(Tiles.GRASS, new TextureRegion(tilesTexture, 0, 0, 32, 32));
        tiles.put(Tiles.DIRT, new TextureRegion(tilesTexture, 32, 0, 32, 32));
        tiles.put(Tiles.SAW, new TextureRegion(tilesTexture, 64, 0, 64, 64));

        initUI();

        initTask.apply(this);
    }

    private void initUI() {
        debugValuesPanel = new DebugValuesPanel(uiNode.getUiSkin());
        debugValuesPanel.setVisible(false);

        toolbox = new Toolbox(uiNode.getUiSkin(), new Toolbox.Listener() {
            @Override
            public void loadFileRequested() {
                openLevelFile();
            }

            @Override
            public void saveFileRequested() {
                saveDialog.show();
            }
        });

        uiNode.getContentTable().add(toolbox).expandX().fill().row();
        uiNode.getContentTable().add(debugValuesPanel).left().row();

        saveDialog = new LevelSaveDialog(uiNode.getStage(), "Save Level", uiNode.getUiSkin());
        loadDialog = new LevelLoadDialog(uiNode.getStage(), "Load Level", uiNode.getUiSkin());

        saveDialog.setListener(new Dialog.Listener<LevelSaveDialog.Event>() {
            @Override
            public void dialogAccepted(LevelSaveDialog.Event event) {
                setBoundLevelFile(new File("levels/" + event.getFilename() + ".dat"));
                saveLevel();
            }
        });

        loadDialog.setListener(new Dialog.Listener<LevelLoadDialog.Event>() {
            @Override
            public void dialogAccepted(LevelLoadDialog.Event event) {
                log.debug("Requested to load the level " + event.getLevelName());

                try {
                    loadLevel(new File("levels/" + event.getLevelName() + ".dat"));
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
            }
        });
    }

    private void openLevelFile() {
        loadDialog.setAvailableLevelsList(discoverAvailableLevels());
        loadDialog.show();
    }

    public void loadLevel(File selectedFile) {
        try {
            log.debug("Attempting to load file " + selectedFile);
            FileInputStream inputStream = new FileInputStream(selectedFile);

            Level level = Serialization.deserialize(inputStream);

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

    private void doUpdate() {
        Camera camera = RenderSystem.instance().getCamera();
        float zoom = RenderSystem.instance().getZoom();

        DebugValues.instance().setValue(DebugItems.CAMERA_POSITION, (int) camera.position.x + ", " + (int) camera.position.y);
        DebugValues.instance().setValue(DebugItems.ZOOM, String.valueOf(zoom * 100) + " %");
        DebugValues.instance().setValue(DebugItems.MOUSE_VIEWPORT, Gdx.input.getX() + ", " + Gdx.input.getY());

        Vector2 mouseWorld = RenderSystem.instance().screenToWorld(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        DebugValues.instance().setValue(DebugItems.MOUSE_WORLD, (int) mouseWorld.x + ", " + (int) mouseWorld.y);

        if (level != null) {
            terrainSprite = SPRITE_FACTORY.createSprite(level.getTerrainPatches().get(0));
            terrainSprite.setColor(Color.BLACK);
        }
    }

    @Override
    public void resize(int w, int h) {
        RenderSystem.instance().setViewportSize(w, h);

        uiNode.getStage().getViewport().update(w, h, true);
    }

    void setLevel(Level level) {
        this.level = level;

        terrainSprite = SPRITE_FACTORY.createSprite(level.getTerrainPatches().get(0));
        terrainSprite.setColor(Color.BLACK);

        if (terrainPolygonEditor != null) {
            root.removeNode(terrainPolygonEditor.getNode());
        }
        terrainPolygonEditor = new PolygonEditor(level.getTerrainPatches().get(0));
        root.addNode(terrainPolygonEditor.getNode());
    }

    private List<String> discoverAvailableLevels() {
        List<String> result = Lists.newArrayList();

        File levelsDirectory = new File("levels/");
        for (File possibleLevelFile : levelsDirectory.listFiles()) {
            if (possibleLevelFile.getAbsolutePath().endsWith(".dat")) {
                result.add(possibleLevelFile.getName().substring(0, possibleLevelFile.getName().length() - 4));
            }
        }

        return result;
    }

    public static void launch(Function<LevelEditor, Boolean> initTask) {
        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.width = 1000;
        config.height = 800;
        config.fullscreen = false;
        config.title = "Level Editor - " + Version.value();
        new LwjglApplication(new LevelEditor(initTask), config);
    }
}
