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

Create or checkout the Aiimi corresponding branch. Merge the **branch_3x** into your **aiimi_3x** branch

Set the head to the Tag...

```
git revert --hard 3.2.2
```