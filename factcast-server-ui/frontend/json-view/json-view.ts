import {css, html, LitElement, PropertyValues} from "lit";
import {customElement, query} from "lit/decorators.js";
import * as monaco from "monaco-editor";
import monacoCss from "monaco-editor/min/vs/editor/editor.main.css?inline";
// @ts-ignore
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
// @ts-ignore
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import {IDisposable, IRange, languages} from "monaco-editor";
import {visit, JSONPath} from "jsonc-parser";
import {JSONPath as jp} from "jsonpath-plus";

type FactFilterOptions = {
    aggregateId?: string;
    meta?: {
        key: string;
        value: string;
    }
};

type FactMetaData = {
    annotations: Record<string, string[]>;
    hoverContent: Record<string, string[]>;
    filterOptions: Record<string, FactFilterOptions>;
};

type EnrichedMember = { range: IRange } & Partial<languages.CodeLens> &
    Partial<languages.Hover>;

type CompiledPath = {
    originalPath: string;
    compiledPath: JSONPath;
};

type UpdateFactFilterOptions = FactFilterOptions & {
    affectedCriteria: number;
}

type VaadinJsonViewElement = {
    $server: {
        updateFilters: (o: string) => void;
    }
}

@customElement("json-view")
class JsonView extends LitElement {
    private editor: monaco.editor.IStandaloneCodeEditor | null = null;
    private codeLensProvider: IDisposable | null = null;
    private hoverProvider: IDisposable | null = null;

    @query("#monaco-editor")
    private editorDiv: HTMLDivElement | undefined;

    private metaData: EnrichedMember[] = [];
    private filterUpdateCommand: string | null = null;
    private quickFilterEnabled: boolean = false;
    private vaadinElement: VaadinJsonViewElement | undefined;

    constructor() {
        super();

        console.log(
            `Facts rendered during the lifetime of this component will be logged and can be used for further processing/aggregation.`
        );
    }

    firstUpdated(_changedProperties: PropertyValues) {
        super.firstUpdated(_changedProperties);
        this.setupEditor();
    }

    connectedCallback() {
        super.connectedCallback();
        this.setupEditor();
    }

    disconnectedCallback() {
        super.disconnectedCallback();

        this.editor?.dispose();
        this.codeLensProvider?.dispose();
        this.hoverProvider?.dispose();
        this.editor = null;
    }

    private setupEditor() {
        if (this.editor || !this.editorDiv) {
            return;
        }

        const that = this;

        this.codeLensProvider = monaco.languages.registerCodeLensProvider("json", {
            async provideCodeLenses(model) {

                if (model.getValue() === "") {
                    return {
                        lenses: [],
                        dispose: () => {
                        },
                    };
                }

                return {
                    lenses: that.metaData.filter((x) => x.command != null),
                    dispose: () => {
                    },
                } as languages.CodeLensList;
            },
        });

        this.hoverProvider = monaco.languages.registerHoverProvider("json", {
            async provideHover(model, position) {
                if (model.getValue() === "") {
                    return null;
                }

                const payload = that.metaData.find(
                    ({range}) =>
                        (range.startLineNumber < position.lineNumber ||
                            (range.startLineNumber === position.lineNumber &&
                                range.startColumn <= position.column)) &&
                        (range.endLineNumber > position.lineNumber ||
                            (range.endLineNumber === position.lineNumber &&
                                range.endColumn >= position.column))
                );
                if (!payload) return null;

                if (!payload.contents) return null;

                return payload as languages.Hover;
            },
        });

        this.editor = monaco.editor.create(this.editorDiv, {
            language: "json",
            readOnly: true,
            scrollBeyondLastLine: false,
            minimap: {enabled: false},
            theme: "vs",
            fontLigatures: "",
            automaticLayout: true,
        });

        this.filterUpdateCommand = this.editor.addCommand(0, (ctx, arg: FactFilterOptions) => {
            console.log("!!!!Running command!");
            console.log(arg);
            console.log(this.vaadinElement);
            this.vaadinElement?.$server.updateFilters(JSON.stringify({
                ...arg,
                affectedCriteria: -1
            }))
        })
    }

    public renderJson(json: string, metaData: string, enableQuickFilter: boolean, vaadinElement: VaadinJsonViewElement) {
        this.quickFilterEnabled = enableQuickFilter;
        this.vaadinElement = vaadinElement;
        if (this.editor) {
            this.metaData = this.parseMetaData(json, metaData);
            console.log(this.metaData);
            this.editor.setValue(json);

            console.dir((JSON.parse(json) as []).reverse());
        }
    }

    private parseMetaData(content: string, metaData: string) {
        const parsedMetaData = JSON.parse(metaData) as FactMetaData;
        const enrichedMembers: EnrichedMember[] = [];

        const annotationMap: CompiledPath[] = Object.keys(
            parsedMetaData.annotations
        ).map((path) => ({
            originalPath: path,
            compiledPath: this.compilePath(path),
        }));

        const hoverMap: CompiledPath[] = Object.keys(
            parsedMetaData.hoverContent
        ).map((path) => ({
            originalPath: path,
            compiledPath: this.compilePath(path),
        }));

        const filterOptionsMap: CompiledPath[] = Object.keys(
            parsedMetaData.filterOptions
        ).map((path) => ({
            originalPath: path,
            compiledPath: this.compilePath(path),
        }));

        const that = this;

        visit(content, {
            onObjectProperty(
                property: string,
                offset: number,
                length: number,
                startLine: number,
                startCharacter: number,
                pathSupplier: () => JSONPath
            ) {
                const finalPath = JSON.stringify([...pathSupplier(), property]);
                const annotation = annotationMap.find(
                    (x) => JSON.stringify(x.compiledPath) === finalPath
                );

                const hoverContent = hoverMap.find(
                    (x) => JSON.stringify(x.compiledPath) === finalPath
                );

                const filterOptionsContent = filterOptionsMap.find(
                    (x) => JSON.stringify(x.compiledPath) === finalPath
                );

                if (!annotation && !hoverContent && !filterOptionsContent) return;
                if (annotation || hoverContent) {
                    const enrichedMember: EnrichedMember = {
                        range: new monaco.Range(
                            startLine + 1,
                            startCharacter + 1, // +1  zero based index
                            startLine + 1,
                            startCharacter + property.length + 3 // +2 for the quote and zero based index
                        ),
                    };

                    if (annotation?.originalPath) {
                        enrichedMember.command = {
                            id: "",
                            title:
                                parsedMetaData.annotations[annotation.originalPath].join(", "),
                        };
                    }

                    if (hoverContent?.originalPath) {
                        enrichedMember.contents = parsedMetaData.hoverContent[
                            hoverContent.originalPath
                            ].map((x) => ({
                            isTrusted: true,
                            value: x,
                        }));
                    }
                    enrichedMembers.push(enrichedMember);
                } else if (that.quickFilterEnabled && filterOptionsContent && filterOptionsContent?.originalPath) {
                    const filter = parsedMetaData.filterOptions[filterOptionsContent.originalPath];
                    // expand range to cover value as well
                    const rangeEnd =
                        startCharacter + 1 // for zero based index
                        + + property.length
                        + 7 // for quotes around property and value and the " : " in the middle
                        + (filter.meta?.value.length ?? filter.aggregateId?.length ?? 0)
                    const enrichedMember: EnrichedMember = {
                        range: new monaco.Range(
                            startLine + 1,
                            startCharacter + 1, // +1  zero based index
                            startLine + 1,
                            rangeEnd
                        ),
                    };


                    enrichedMember.contents = [];

                    const encodedArgs = encodeURIComponent(JSON.stringify(filter));
                    if (filter.meta) {
                        enrichedMember.contents.push({
                            isTrusted: true,
                            value: `[Filter for ${filter.meta.key}:${filter.meta.value} on all conditions](command:${that.filterUpdateCommand}?${encodedArgs})`
                        })
                        // todo make list of conditions available here and add a "Filter on condition #"
                    }


                    enrichedMembers.push(enrichedMember);
                }

            },
        });

        return enrichedMembers;
    }

    private compilePath(path: string) {
        return jp
            .toPathArray(path)
            .filter((x) => x !== "..")
            .filter((x) => x !== "$")
            .map((x) => this.getNumber(x));
    }

    private getNumber(s: string) {
        const num = parseInt(s, 10);
        return isNaN(num) ? s : num;
    }

    render() {
        return html`
            <div id="monaco-editor"></div>`;
    }

    static styles = [
        monacoCss,
        css`
            :host {
                width: 100%;
                flex-grow: 1;
                display: flex;
            }

            #monaco-editor {
                flex-grow: 1;
                width: 100%;
                border: 1px solid var(--lumo-contrast-20pct);
            }
        `,
    ];
}

// @ts-ignore
self.MonacoEnvironment = {
    getWorker(_: any, label: string) {
        switch (label) {
            case "json":
                return new jsonWorker();
            default:
                return new editorWorker();
        }
    },
};
