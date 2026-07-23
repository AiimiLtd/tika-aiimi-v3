# Syncing with Tika

In Github, use **Sync Fork**.

Do the same to double check with the brach that correlates with the target version e.g. **branch_3x**

Pull that branch locally, then to sync the tags, in the terminal use:

```
git fetch --tags https://github.com/apache/tika
```

Check the tags, with:

```
git tag
```

Once you are happy all the tags are pulled, then push the branch:

```
git push
```

## Use the release Tag

Set the head to the targetted release Tag...

```
git reset --hard 3.3.2
```

## Branch from the Tag

Create the Aiimi corresponding branch from this TAG.

```
git branch aiimi-3.3.2
```

Cherry-pick the Aiimi fixes and workflows using the **aiimi-custom** label (a git tag on the consolidating Aiimi commit), not commit hashes:

```
git fetch --tags
git cherry-pick aiimi-custom
```

That single commit should include the timeout handling, Docker/release scripts, and related Aiimi files.

### After changing the Aiimi commit

If you rebuild the Aiimi changes into a new consolidating commit, retarget the label and push it:

```
git tag -f -a aiimi-custom -m "AIIMI customisations (timeout, docker, release)"
git push origin aiimi-custom --force
```

### Test build and update scripts

Do a build with ```./build.sh``` to verify it compiles.

Check for the jar file: **tika-server/tika-server-standard/target/tika-server-standard-3.3.2.jar**

The name may be different, but as it is built from a Tag, it should follow this convention.

Edit the **DockerFile** in the root folder, to reflect the jar file name.

Edit the **docker-release.yml** in the **.github/workflows** folder, to reflect the jar file name.

Commit and push changes, then ensure **aiimi-custom** points at that commit (see above).

# Release

Start a Release from the branch you just created/updated, using the Tag convention **v3.3.2**. NOT **3.3.2** as this would conflict with the existing Tag.

Once the release is complete, you can then update the **aie.json** file to reflect this new tag in the InsightMaker repo. Test it in Docker and the new image will be spun up for you.
