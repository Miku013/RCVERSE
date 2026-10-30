(function() {
    let mcPanel;
    let copyJsonAction;

    // GeckoLib / Minecraft 座標変換ユーティリティ
    const MCUtils = {
        toMinecraftPos(pos) {
            if (!pos) return [0, 0, 0];
            // ピボット（位置）は Blockbench の数値をそのまま .geo.json に書くのが GeckoLib の仕様
            return [
                parseFloat(pos[0] || 0),
                parseFloat(pos[1] || 0),
                parseFloat(pos[2] || 0)
            ];
        },
        toMinecraftRot(rot) {
            if (!rot) return [0, 0, 0];
            // GeckoLibは読み込み時に -X, -Y, +Z するため、
            // XとYを反転させておけば、マイクラ内でBlockbenchと完全に同じ向きになります！
            return [
                -parseFloat(rot[0] || 0), // X軸の符号反転
                -parseFloat(rot[1] || 0), // Y軸の符号反転
                parseFloat(rot[2] || 0)   // Z軸はそのまま
            ];
        }
    };

    // Blockbenchの全バージョン対応の安全な選択オブジェクト取得関数
    function getActiveSelection() {
        // 1. グループ（ボーン）が選択されている場合
        if (typeof Group !== 'undefined' && Group.selected) {
            if (Array.isArray(Group.selected) && Group.selected.length > 0) {
                return Group.selected[0];
            } else if (!Array.isArray(Group.selected) && Group.selected.name) {
                return Group.selected;
            }
        }
        // 2. キューブ（直方体）が選択されている場合
        if (typeof Cube !== 'undefined' && Cube.selected) {
            if (Array.isArray(Cube.selected) && Cube.selected.length > 0) {
                return Cube.selected[0];
            } else if (!Array.isArray(Cube.selected) && Cube.selected.name) {
                return Cube.selected;
            }
        }
        // 3. 汎用 selected コレクション
        if (typeof selected !== 'undefined' && Array.isArray(selected) && selected.length > 0) {
            return selected[0];
        }
        return null;
    }

    Plugin.register('minecraft_coords_mode', {
        title: 'Minecraft Coordinate Mode',
        author: 'Miku & Antigravity',
        description: '右側サイドバーにMinecraft Java / GeckoLib基準の座標・回転をリアルタイム表示・コピーするパネルを追加します。',
        icon: 'tune',
        version: '1.0.5',
        variant: 'both',
        onload() {
            // GeckoLib ボーン JSON コピーアクション
            copyJsonAction = new Action('copy_geckolib_bone_json', {
                name: 'GeckoLib ボーン定義JSONをコピー',
                description: '選択中ボーンの Minecraft / GeckoLib 基準の pivot と rotation をJSON形式でコピーします',
                icon: 'content_copy',
                category: 'tools',
                keybind: new Keybind({ key: 'c', ctrl: true, shift: true }),
                click: function() {
                    let obj = getActiveSelection();
                    if (!obj) {
                        Blockbench.showQuickMessage('パーツ（ボーンまたはキューブ）を選択してください', 2000);
                        return;
                    }

                    let origin = obj.origin || [0, 0, 0];
                    let rotation = obj.rotation || [0, 0, 0];

                    let mcPivot = MCUtils.toMinecraftPos(origin);
                    let mcRot = MCUtils.toMinecraftRot(rotation);

                    let parentName = 'root';
                    if (obj.parent && obj.parent.name) {
                        parentName = obj.parent.name;
                    }

                    let snippet = {
                        name: obj.name,
                        parent: parentName,
                        pivot: [
                            parseFloat(mcPivot[0].toFixed(4)),
                            parseFloat(mcPivot[1].toFixed(4)),
                            parseFloat(mcPivot[2].toFixed(4))
                        ],
                        rotation: [
                            parseFloat(mcRot[0].toFixed(4)),
                            parseFloat(mcRot[1].toFixed(4)),
                            parseFloat(mcRot[2].toFixed(4))
                        ]
                    };

                    let jsonStr = JSON.stringify(snippet, null, '\t');
                    navigator.clipboard.writeText(jsonStr).then(() => {
                        Blockbench.showQuickMessage('GeckoLib ボーンJSONをクリップボードにコピーしました！', 2500);
                    });
                }
            });
            MenuBar.addAction(copyJsonAction, 'tools');

            // 右側サイドバーパネル
            mcPanel = new Panel('mc_coords_panel', {
                title: 'Minecraft Coordinates',
                icon: 'tune',
                default_side: 'right',
                component: {
                    template: `
                        <div class="panel_contents" style="padding: 10px; font-size: 12px; line-height: 1.5;">
                            <div style="font-weight: bold; margin-bottom: 8px; color: var(--color-accent); border-bottom: 1px solid var(--color-border); padding-bottom: 4px;">
                                🎯 選択中: {{ selectedName }} ({{ objType }})
                            </div>

                            <div style="background: var(--color-ui); padding: 8px; border-radius: 4px; margin-bottom: 10px; border-left: 3px solid var(--color-accent);">
                                <div style="font-weight: bold; color: var(--color-accent); margin-bottom: 4px;">
                                    🎮 Minecraft / GeckoLib 基準
                                </div>
                                <div style="font-family: monospace;"><b>Pos/Pivot:</b> [ X: <span style="color: #55ff55; font-weight: bold;">{{ mcPosX }}</span>, Y: <span style="color: #ffaa00; font-weight: bold;">{{ mcPosY }}</span>, Z: <span style="color: #55ffff; font-weight: bold;">{{ mcPosZ }}</span> ]</div>
                                <div style="font-family: monospace;"><b>Rotation:</b> [ X: <b>{{ mcRotX }}°</b>, Y: <b>{{ mcRotY }}°</b>, Z: <b>{{ mcRotZ }}°</b> ]</div>
                            </div>

                            <div style="background: var(--color-subtle); padding: 8px; border-radius: 4px; margin-bottom: 10px; opacity: 0.85;">
                                <div style="font-weight: bold; margin-bottom: 4px;">
                                    📦 Blockbench 内部値
                                </div>
                                <div style="font-family: monospace;"><b>Origin:</b> [ X: {{ bbPosX }}, Y: {{ bbPosY }}, Z: {{ bbPosZ }} ]</div>
                                <div style="font-family: monospace;"><b>Rotation:</b> [ X: {{ bbRotX }}°, Y: {{ bbRotY }}°, Z: {{ bbRotZ }}° ]</div>
                            </div>

                            <button style="width: 100%; padding: 8px; cursor: pointer; font-weight: bold; background: var(--color-button); color: var(--color-text); border: 1px solid var(--color-border); border-radius: 4px;"
                                    @click="copySnippet" :disabled="!hasSelection">
                                📋 GeckoLib ボーンJSONをコピー
                            </button>
                            <div style="font-size: 10px; color: var(--color-subtle_text); margin-top: 4px; text-align: center;">
                                ショートカット: Ctrl + Shift + C
                            </div>
                        </div>
                    `,
                    data() {
                        return {
                            selectedName: '（未選択）',
                            objType: '-',
                            hasSelection: false,
                            bbPosX: '0.00', bbPosY: '0.00', bbPosZ: '0.00',
                            bbRotX: '0.0', bbRotY: '0.0', bbRotZ: '0.0',
                            mcPosX: '0.00', mcPosY: '0.00', mcPosZ: '0.00',
                            mcRotX: '0.0', mcRotY: '0.0', mcRotZ: '0.0'
                        };
                    },
                    methods: {
                        update() {
                            let obj = getActiveSelection();
                            if (!obj) {
                                this.selectedName = '（未選択）';
                                this.objType = '-';
                                this.hasSelection = false;
                                return;
                            }

                            this.hasSelection = true;
                            this.selectedName = obj.name || '要素';
                            this.objType = (typeof Group !== 'undefined' && obj instanceof Group) ? 'ボーン/グループ' : 'キューブ';

                            // 座標（Pivot / Origin）の抽出
                            let pos = [0, 0, 0];
                            if (obj.origin && Array.isArray(obj.origin)) {
                                pos = obj.origin;
                            } else if (obj.from && obj.to) {
                                pos = [
                                    (obj.from[0] + obj.to[0]) / 2,
                                    (obj.from[1] + obj.to[1]) / 2,
                                    (obj.from[2] + obj.to[2]) / 2
                                ];
                            }

                            // 回転角（Rotation）の抽出
                            let rot = [0, 0, 0];
                            if (obj.rotation && Array.isArray(obj.rotation)) {
                                rot = obj.rotation;
                            }

                            this.bbPosX = Number(pos[0] || 0).toFixed(2);
                            this.bbPosY = Number(pos[1] || 0).toFixed(2);
                            this.bbPosZ = Number(pos[2] || 0).toFixed(2);

                            this.bbRotX = Number(rot[0] || 0).toFixed(1);
                            this.bbRotY = Number(rot[1] || 0).toFixed(1);
                            this.bbRotZ = Number(rot[2] || 0).toFixed(1);

                            let mcP = MCUtils.toMinecraftPos(pos);
                            let mcR = MCUtils.toMinecraftRot(rot);

                            this.mcPosX = Number(mcP[0] || 0).toFixed(2);
                            this.mcPosY = Number(mcP[1] || 0).toFixed(2);
                            this.mcPosZ = Number(mcP[2] || 0).toFixed(2);

                            this.mcRotX = Number(mcR[0] || 0).toFixed(1);
                            this.mcRotY = Number(mcR[1] || 0).toFixed(1);
                            this.mcRotZ = Number(mcR[2] || 0).toFixed(1);
                        },
                        copySnippet() {
                            copyJsonAction.click();
                        }
                    },
                    mounted() {
                        this.update();
                        this.interval = setInterval(() => {
                            this.update();
                        }, 150);
                    },
                    destroyed() {
                        if (this.interval) clearInterval(this.interval);
                    }
                }
            });

            Blockbench.showQuickMessage('Minecraft Coordinates パネルを読み込みました！', 2500);
        },
        onunload() {
            if (copyJsonAction) copyJsonAction.delete();
            if (mcPanel) mcPanel.delete();
        }
    });
})();
