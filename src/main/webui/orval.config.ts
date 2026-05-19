import { defineConfig } from "orval";

export default defineConfig({
  sesame: {
    input: {
      target: "./openapi/openapi.json",
    },
    output: {
      mode: "split",
      target: "src/api/generated.ts",
      schemas: "src/api/model",
      client: "fetch",
      baseUrl: "",
      override: {
        mutator: {
          path: "./src/api/fetcher.ts",
          name: "fetcher",
        },
      },
    },
  },
});
