export const PROTOCOL_VERSION = 1
export const BUILD_VERSION: string = '0.1.0'

export function buildVersionsCompatible(serverBuildVersion: string): boolean {
  return (
    serverBuildVersion === 'dev' ||
    BUILD_VERSION === 'dev' ||
    serverBuildVersion === BUILD_VERSION
  )
}
