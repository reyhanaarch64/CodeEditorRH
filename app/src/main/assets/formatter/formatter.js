(() => {
    let pythonWorkspace;

    async function formatCode(language, source) {
        try {
            let result;
            switch (language) {
                case "javascript":
                    result = await prettier.format(source, {
                        parser: "babel",
                        plugins: [prettierPlugins.babel, prettierPlugins.estree],
                        semi: true,
                        singleQuote: true
                    });
                    break;
                case "html":
                    result = await prettier.format(source, {
                        parser: "html",
                        plugins: [prettierPlugins.html]
                    });
                    break;
                case "css":
                    result = await prettier.format(source, {
                        parser: "css",
                        plugins: [prettierPlugins.postcss]
                    });
                    break;
                case "php":
                    result = await prettier.format(source, {
                        parser: "php",
                        plugins: [prettierPlugins.php]
                    });
                    break;
                case "python": {
                    const {default: initialize, PositionEncoding, Workspace} =
                        await import("./ruff_wasm.js");
                    if (!pythonWorkspace) {
                        await initialize();
                        pythonWorkspace = new Workspace(
                            Workspace.defaultSettings(),
                            PositionEncoding.Utf16
                        );
                    }
                    result = pythonWorkspace.format(source);
                    break;
                }
                default:
                    throw new Error("bahasa ini belum didukung");
            }
            AndroidFormatter.onFormatted(result);
        } catch (error) {
            AndroidFormatter.onError(error && error.message ? error.message : String(error));
        }
    }

    window.formatCode = formatCode;
})();
