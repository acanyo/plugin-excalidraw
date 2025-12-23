import { axiosInstance } from "@halo-dev/api-client";
import {
  ApiExcalidrawXhhaoComV1alpha1DrawingApi,
  DrawingV1alpha1Api
} from "./generated";

const excalidrawCoreApiClient = new DrawingV1alpha1Api(undefined, "", axiosInstance)
const apiExcalidrawCoreApiClient = new ApiExcalidrawXhhaoComV1alpha1DrawingApi(undefined, "", axiosInstance)

export { excalidrawCoreApiClient, apiExcalidrawCoreApiClient };
