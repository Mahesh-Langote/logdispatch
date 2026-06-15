# Contributing to LogDispatch

First off, thank you for considering contributing to LogDispatch! It's people like you that make LogDispatch such a great tool.

## Where do I go from here?

If you've noticed a bug or have a feature request, make sure to check our [Issues](../../issues) to see if someone else in the community has already created a ticket. If not, go ahead and [make one](../../issues/new/choose)!

## Branching Strategy & Java Versions

We maintain two primary development branches to support different Java ecosystems:

* **`main`**: The primary branch for Java 17+. All new features and developments happen here.
* **`main-java8`**: The branch for Java 8 compatibility. Critical bug fixes or core features from `main` are cherry-picked into this branch by maintainers. The Maven version ends with the `-java8` suffix.

**When contributing:**
Always target your Pull Requests against the **`main`** branch. If your PR includes a critical bug fix, maintainers will handle cherry-picking it into the `main-java8` branch for Java 8 users.

## Fork & create a branch

If this is something you think you can fix, then [fork LogDispatch](https://help.github.com/articles/fork-a-repo) and create a branch with a descriptive name.

A good branch name would be (where issue #325 is the ticket you're working on):

```sh
git checkout -b 325-add-graphql-support
```

## Get the test suite running

Make sure you have JDK 21+ installed.
Run the Maven test suite to ensure everything is working locally before you make changes.

```sh
./mvnw clean test
```

## Implement your fix or feature

At this point, you're ready to make your changes. Feel free to ask for help; everyone is a beginner at first.

## Make a Pull Request

At this point, you should switch back to your master branch and make sure it's up to date with LogDispatch's master branch:

```sh
git remote add upstream git@github.com:Mahesh-Langote/logdispatch.git
git checkout main
git pull upstream main
```

Then update your feature branch from your local copy of main, and push it!

```sh
git checkout 325-add-graphql-support
git rebase main
git push --set-upstream origin 325-add-graphql-support
```

Finally, go to GitHub and [make a Pull Request](../../pulls) with your changes. We'll review it as soon as we can.
