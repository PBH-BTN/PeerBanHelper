/**
 * This hook is called after all packages have been resolved.
 * To remove the tarball URLs from the lockfile.
 * 
 * Because the pnpm config lockfile-include-tarball-url cannot work,
 * we need to remove the tarball URLs from the lockfile manually.
 * @see https://github.com/pnpm/pnpm/issues/6667#issuecomment-2971121163
 */
function afterAllResolved(lockfile) {
  // Remove the tarball URLs from the lockfile
  for (const key in lockfile.packages) {
    if (lockfile.packages[key].resolution?.tarball) {
      delete lockfile.packages[key].resolution.tarball;
    }
  }
  return lockfile;
}

module.exports = {
  hooks: {
    afterAllResolved,
  },
};