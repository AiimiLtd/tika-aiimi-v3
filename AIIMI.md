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
git reset --hard 3.2.2
```

## Branch from the Tag

Create the Aiimi corresponding branch from this TAG.

```
git branch aiimi_3_2_2
```

Cherry-pick the Aiimi fixes and workflows:

```
git cherry-pick 865d4ef6b0e605bf895eab52d988882e1c09537d
git cherry-pick cb5ab5b926489b17c5e443b20c6038ac26135ab8
```

There may one or two others in there, so check ```git log``` on the previous release branch.

### Test build and update scripts

Do a build with ```./build.sh``` to verify it compiles.

Check for the jar file: **tika-server/tika-server-standard/target/tika-server-standard-3.2.2.jar**

The name may be different, but as it is built from a Tag, it should follow this convention.

Edit the **DockerFile** in the root folder, to reflect the jar file name.

Commit and push changes.

# Release

Start a Release from the branch you just created/updated, using the Tag convention **v3.2.2**. NOT **3.2.2** as this would conflict with the existing Tag.

Once the release is complete, you can then update the **aie.json** file to reflect this new tag in the InsightMaker repo. Test it in Docker and the new image will be spun up for you.